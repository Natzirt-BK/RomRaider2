/*
 * RomRaider Open-Source Tuning, Logging and Reflashing
 * Copyright (C) 2006-2022 RomRaider.com
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package com.romraider.logger.ecu.comms.manager;

import static com.romraider.logger.ecu.comms.io.connection.LoggerConnectionFactory.getConnection;
import static com.romraider.logger.ecu.definition.EcuDataType.EXTERNAL;
import static com.romraider.util.ParamChecker.checkNotNull;
import static com.romraider.util.ParamChecker.isNullOrEmpty;
import static com.romraider.util.ThreadUtil.sleep;
import static java.lang.System.currentTimeMillis;
import static java.util.Collections.synchronizedList;
import static java.util.Collections.synchronizedMap;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.concurrent.atomic.AtomicReference;
import com.romraider.logger.ecu.comms.query.dimemod.DmInitCallback;
import org.apache.log4j.Logger;

import com.romraider.Settings;
import com.romraider.io.j2534.api.J2534Library;
import com.romraider.io.j2534.api.J2534LibraryLocator;
import com.romraider.logger.ecu.comms.io.connection.LoggerConnection;
import com.romraider.logger.ecu.comms.query.EcuInitCallback;
import com.romraider.logger.ecu.comms.query.InitializationAttempt;
import com.romraider.logger.ecu.comms.query.EcuQuery;
import com.romraider.logger.ecu.comms.query.EcuQueryImpl;
import com.romraider.logger.ecu.comms.query.ExternalQuery;
import com.romraider.logger.ecu.comms.query.ExternalQueryImpl;
import com.romraider.logger.ecu.comms.query.Query;
import com.romraider.logger.ecu.comms.query.Response;
import com.romraider.logger.ecu.comms.query.ResponseImpl;
import com.romraider.logger.ecu.definition.EcuData;
import com.romraider.logger.ecu.definition.ExternalData;
import com.romraider.logger.ecu.definition.LoggerData;
import com.romraider.logger.ecu.definition.Module;
import com.romraider.logger.api.LoggerStatusListener;
import com.romraider.logger.ecu.ui.MessageListener;
import com.romraider.logger.ecu.ui.handler.DataUpdateHandler;
import com.romraider.logger.ecu.ui.handler.file.FileLoggerControllerSwitchMonitor;
import com.romraider.util.ResourceUtil;
import com.romraider.util.SettingsManager;

public final class QueryManagerImpl implements QueryManager {
    private static final Logger LOGGER = Logger.getLogger(QueryManagerImpl.class);
    private static final ResourceBundle rb = new ResourceUtil().getBundle(
            QueryManagerImpl.class.getName());
    private final List<LoggerStatusListener> listeners =
            synchronizedList(new ArrayList<LoggerStatusListener>());
    private final Map<String, Query> queryMap =
            synchronizedMap(new HashMap<String, Query>());
    private final Map<String, Query> addList = new HashMap<String, Query>();
    private final List<String> removeList = new ArrayList<String>();
    private static final PollingState pollState = new PollingStateImpl();
    private static final Settings settings = SettingsManager.getSettings();
    private static final String EXT = "Externals";
    private static final long INITIAL_RETRY_DELAY_MS = 1000L;
    private static final long MAXIMUM_RETRY_DELAY_MS = 5000L;
    private final EcuInitCallback ecuInitCallback;
    private final DmInitCallback dmInitCallback;
    private final MessageListener messageListener;
    private final Consumer<Runnable> notificationExecutor;
    private final Supplier<LoggerConnection> connectionFactory;
    private final AtomicReference<InitializationAttempt> activeInitialization = new AtomicReference<>();
    private static final class FileLoggerBinding {
        final FileLoggerControllerSwitchMonitor monitor;
        final EcuQuery query;
        FileLoggerBinding(FileLoggerControllerSwitchMonitor monitor) {
            this.monitor = monitor;
            query = new EcuQueryImpl(monitor.getEcuSwitch());
        }
    }
    private volatile FileLoggerBinding fileLoggerBinding;
    private FileLoggerBinding queriedFileLoggerBinding;
    private Thread queryManagerThread;
    private volatile boolean started;
    private volatile boolean stop;
    private AsyncDataUpdateHandler dataUpdater;
    private DataUpdateHandler[] updateHandlers;
    private int queryCounter;
    private long queryStart;
    private boolean initFailureReported;

    public QueryManagerImpl(EcuInitCallback ecuInitCallback,
            DmInitCallback dmInitCallback,
            MessageListener messageListener,
            DataUpdateHandler... dataUpdateHandlers) {
        this(ecuInitCallback, dmInitCallback, messageListener,
                Runnable::run, dataUpdateHandlers);
    }

    public QueryManagerImpl(EcuInitCallback ecuInitCallback,
            DmInitCallback dmInitCallback,
            MessageListener messageListener,
            Consumer<Runnable> notificationExecutor,
            DataUpdateHandler... dataUpdateHandlers) {
        this(ecuInitCallback, dmInitCallback, messageListener, notificationExecutor,
                () -> getConnection(settings.getLoggerProtocol(), settings.getLoggerPort(),
                        settings.getLoggerConnectionProperties()), dataUpdateHandlers);
    }

    /** Synthetic connection seam; constructing a manager never starts its worker. */
    QueryManagerImpl(EcuInitCallback ecuInitCallback,
            DmInitCallback dmInitCallback, MessageListener messageListener,
            Consumer<Runnable> notificationExecutor, Supplier<LoggerConnection> connectionFactory,
            DataUpdateHandler... dataUpdateHandlers) {
        checkNotNull(ecuInitCallback,
                messageListener,
                notificationExecutor,
                connectionFactory,
                dataUpdateHandlers);
        this.ecuInitCallback = ecuInitCallback;
        this.dmInitCallback = dmInitCallback;
        this.messageListener = messageListener;
        this.notificationExecutor = notificationExecutor;
        this.connectionFactory = connectionFactory;
        this.updateHandlers = dataUpdateHandlers;
        stop = true;
    }

    @Override
    public synchronized void addListener(LoggerStatusListener listener) {
        checkNotNull(listener, "listener");
        listeners.add(listener);
    }

    @Override
    public void setFileLoggerSwitchMonitor(FileLoggerControllerSwitchMonitor monitor) {
        fileLoggerBinding = monitor == null ? null : new FileLoggerBinding(monitor);
        pollState.setNewQuery(true);
    }

    @Override
    public synchronized void addQuery(String callerId, LoggerData loggerData) {
        checkNotNull(callerId, loggerData);

        //Reset stats
        queryCounter = 1;
        queryStart = currentTimeMillis();

        //FIXME: This is a hack!!
        String queryId = buildQueryId(callerId, loggerData);
        removeList.remove(queryId);
        if (loggerData.getDataType() == EXTERNAL) {
            addList.put(queryId, new ExternalQueryImpl((ExternalData) loggerData));
        } else {
            addList.put(queryId, new EcuQueryImpl((EcuData) loggerData));
            pollState.setLastQuery(false);
            pollState.setNewQuery(true);
        }
    }

    @Override
    public synchronized void removeQuery(String callerId, LoggerData loggerData) {
        checkNotNull(callerId, loggerData);

        //Reset stats
        queryCounter = 1;
        queryStart = currentTimeMillis();

        String queryId = buildQueryId(callerId, loggerData);
        addList.remove(queryId);
        removeList.add(queryId);
        if (loggerData.getDataType() != EXTERNAL) {
            pollState.setNewQuery(true);
        }

    }

    @Override
    public Thread getThread() {
        return queryManagerThread;
    }

    @Override
    public boolean isRunning() {
        return started && !stop;
    }

    @Override
    public void run() {
        started = true;
        queryManagerThread = Thread.currentThread();
        queryManagerThread.setName("Query Manager");
        if (LOGGER.isDebugEnabled())
            LOGGER.debug("QueryManager started.");

        try {
            stop = false;
            initFailureReported = false;
            boolean reconnecting = false;
            long retryDelay = INITIAL_RETRY_DELAY_MS;

            while (!stop) {
                if (reconnecting) notifyReconnecting();
                else notifyConnecting();
                Module target = settings.getDestinationTarget();

                if (!settings.isLogExternalsOnly() &&  doEcuInit(target)) {
                    initFailureReported = false;
                    retryDelay = INITIAL_RETRY_DELAY_MS;
                    notifyReading();
                    runLogger(target);
                    if (!stop) reconnecting = true;
                } else if (settings.isLogExternalsOnly()) {
                    notifyReading();
                    runLogger(null);
                } else {
                    if (stop) break;
                    if (reconnecting) notifyReconnecting();
                    else notifyConnecting();
                    messageListener.reportMessage(rb.getString(reconnecting ? "RECONNECTING" : "CONNECTRETRY"));
                    sleep(retryDelay);
                    retryDelay = nextRetryDelay(retryDelay);
                }
            }
        } catch (Exception e) {
            messageListener.reportError(e);
        } finally {
            started = false;
            stop = true;
            cancelInitialization();
            notifyStopped();
            messageListener.reportMessage(rb.getString("DISCONNECTED"));
            if (LOGGER.isDebugEnabled())
                LOGGER.debug("QueryManager stopped.");

            if (dataUpdater != null) {
                dataUpdater.stopUpdater();
            }
        }
    }

    private boolean doEcuInit(Module module) {

        final Set<J2534Library> libraries = J2534LibraryLocator.getLibraries(
                settings.getTransportProtocol().toUpperCase());

        if (isNullOrEmpty(settings.getJ2534Device())) {
            // No previous J2534 library selected in settings
            for (J2534Library dll : libraries) {
                logConnectionAttempt(String.format(
                        "Trying new J2534/%s connection: %s",
                        settings.getTransportProtocol(),
                        dll.getVendor()));

                settings.setJ2534Device(dll.getLibrary());
                if (initConnection(module, dll.getVendor())) {
                    return true;
                }
            }
        }
        else {
            // Try previous J2534 library from settings
            logConnectionAttempt(String.format(
                    "Trying previous J2534/%s connection: %s",
                    settings.getTransportProtocol(),
                    settings.getJ2534Device()));
            if (initConnection(module, settings.getJ2534Device())) {
                return true;
            }
        }
        settings.setJ2534Device("");
        // Finally try Serial
        if (shouldTrySerialConnection(settings.getLoggerPort())
                && initConnection(module, settings.getLoggerPort())) {
            return true;
        }
        return false;
    }

    boolean initConnection(final Module module, final String name) {
        LoggerConnection connection = null;
        boolean rv = false;
        InitializationAttempt attempt = new InitializationAttempt();
        InitializationAttempt previous = activeInitialization.getAndSet(attempt);
        if (previous != null) previous.close();
        // Covers stop racing with installation of the new token.
        if (stop) attempt.close();
        try {
            requireInitializationActive(attempt);
            messageListener.reportMessage(MessageFormat.format(
                    rb.getString("SENDINIT"), module.getName(), name));
            connection = connectionFactory.get();
            requireInitializationActive(attempt);
            connection.ecuInit(attempt.bind(ecuInitCallback), module);
            requireInitializationActive(attempt);
            messageListener.reportMessage(MessageFormat.format(
                    rb.getString("INITDONE"), module.getName(), name));
            try {
                if (dmInitCallback != null) {
                    messageListener.reportMessage(MessageFormat.format(
                            rb.getString("SENDDMINIT"), module.getName(), name));
                    requireInitializationActive(attempt);
                    connection.dmInit(attempt.bind(dmInitCallback), module);
                    requireInitializationActive(attempt);
                    messageListener.reportMessage(MessageFormat.format(
                            rb.getString("INITDMDONE"), module.getName(), name));
                }
            }
            catch (Exception e) {
                if (e instanceof InterruptedException) throw e;
                requireInitializationActive(attempt);
                messageListener.reportMessage(MessageFormat.format(
                        rb.getString("INITDMFAIL"), module.getName()));
                LOGGER.error("Error in DimeMod init: ", e);
            }

            requireInitializationActive(attempt);
            rv = true;
        } catch (Exception e) {
            if (e instanceof InterruptedException || Thread.currentThread().isInterrupted()) {
                stop = true;
                attempt.close();
                Thread.currentThread().interrupt();
            } else if (attempt.isActive()) {
                messageListener.reportMessage(MessageFormat.format(
                        rb.getString("INITFAIL"), module.getName()));
                if (!initFailureReported) {
                    LOGGER.error("Error sending init: ", e);
                    initFailureReported = true;
                } else if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("ECU init still unavailable: " + e.getMessage());
                }
            }
        }
        finally {
            attempt.close();
            activeInitialization.compareAndSet(attempt, null);
            if (connection != null) connection.close();
        }
        return rv;
    }

    /** Observe cancellation after native calls even when they return without throwing. */
    private void requireInitializationActive(InitializationAttempt attempt) {
        if (Thread.currentThread().isInterrupted()) {
            stop = true;
            attempt.close();
        }
        attempt.requireActive();
    }

    static boolean shouldTrySerialConnection(String portName) {
        return !isNullOrEmpty(portName);
    }

    static long nextRetryDelay(long currentDelay) {
        if (currentDelay < INITIAL_RETRY_DELAY_MS) {
            return INITIAL_RETRY_DELAY_MS;
        }
        return Math.min(MAXIMUM_RETRY_DELAY_MS, currentDelay * 2L);
    }

    private void logConnectionAttempt(String message) {
        if (initFailureReported) {
            if (LOGGER.isDebugEnabled()) LOGGER.debug(message);
        } else {
            LOGGER.info(message);
        }
    }

    private void runLogger(Module module) {
        String moduleName = null;
        if (module == null){
            moduleName = EXT;
        }
        else {
            moduleName = module.getName();
        }
        TransmissionManager txManager = new TransmissionManagerImpl();
        queryStart = currentTimeMillis();
        queryCounter = 1;
        long end = currentTimeMillis();
        boolean connectionFailed = false;

        try {
            txManager.start();

            stopDataUpdater();

            dataUpdater = new AsyncDataUpdateHandler(updateHandlers);
            dataUpdater.start();

            boolean lastPollState = settings.isFastPoll();
            while (!stop) {
                dataUpdater.requireHealthy();
                pollState.setFastPoll(settings.isFastPoll());
                queriedFileLoggerBinding = null;
                updateQueryList();
                if (queryMap.isEmpty()) {
                    if (pollState.isLastQuery() &&
                            pollState.getCurrentState() == PollingState.State.STATE_0) {
                        endEcuQueries(txManager);
                        pollState.setLastState(PollingState.State.STATE_0);
                    }

                    messageListener.reportMessage(rb.getString("SELECTPARAMS"));
                    sleep(100L);
                } else {
                    end = currentTimeMillis() + 1L; // update once every 1msec
                    final List<EcuQuery> ecuQueries =
                            filterEcuQueries(queryMap.values());

                    if (!settings.isLogExternalsOnly()) {
                        if (!ecuQueries.isEmpty()) {
                            sendEcuQueries(txManager);
                            if (!pollState.isFastPoll() && lastPollState) {
                                endEcuQueries(txManager);
                            }
                            if (pollState.isFastPoll()) {
                                if (pollState.getCurrentState() == PollingState.State.STATE_0 &&
                                        pollState.isNewQuery()) {
                                    pollState.setCurrentState(PollingState.State.STATE_1);
                                    pollState.setNewQuery(false);
                                }
                                if (pollState.getCurrentState() == PollingState.State.STATE_0 &&
                                        !pollState.isNewQuery()) {
                                    pollState.setCurrentState(PollingState.State.STATE_1);
                                }
                                if (pollState.getCurrentState() == PollingState.State.STATE_1 &&
                                        pollState.isNewQuery()) {
                                    pollState.setCurrentState(PollingState.State.STATE_0);
                                    pollState.setLastState(PollingState.State.STATE_1);
                                    pollState.setNewQuery(false);
                                }
                                if (pollState.getCurrentState() == PollingState.State.STATE_1 &&
                                        !pollState.isNewQuery()) {
                                    pollState.setLastState(PollingState.State.STATE_1);
                                }
                                pollState.setLastQuery(true);
                            }
                            else {
                                pollState.setCurrentState(PollingState.State.STATE_0);
                                pollState.setLastState(PollingState.State.STATE_0);
                                pollState.setNewQuery(false);
                            }
                            lastPollState = pollState.isFastPoll();
                        }
                        else {
                            if (pollState.isLastQuery() &&
                                    pollState.getLastState() == PollingState.State.STATE_1) {
                                endEcuQueries(txManager);
                                pollState.setLastState(PollingState.State.STATE_0);
                                pollState.setCurrentState(PollingState.State.STATE_0);
                                pollState.setNewQuery(true);
                            }
                        }
                    }
                    sendExternalQueries();
                    // waiting until at least 1msec has passed since last query set
                    while (currentTimeMillis() < end) {
                        sleep(1L);
                    }

                    handleQueryResponse();
                    queryCounter++;
                    messageListener.reportMessage(MessageFormat.format(
                            rb.getString("QUERYING"), moduleName));
                    messageListener.reportStats(buildStatsMessage(queryStart, queryCounter));
                }
            }
        } catch (Exception e) {
            connectionFailed = true;
            initFailureReported = true;
            messageListener.reportError(e);
            stopDataUpdater();
            notifyStopped();
            sleep(500L);
        } finally {
          try { stopDataUpdater(); }
          finally {
            messageListener.reportMessage(rb.getString("STOPPING"));
            try {
                txManager.stop();
            } catch (Exception cleanupError) {
                if (connectionFailed) {
                    LOGGER.warn("Unable to close failed Logger connection cleanly",
                            cleanupError);
                } else {
                    LOGGER.error("Unable to close Logger connection cleanly",
                            cleanupError);
                }
            }
            pollState.setCurrentState(PollingState.State.STATE_0);
            pollState.setNewQuery(true);
          }
        }
    }

    private void stopDataUpdater() {
        if (dataUpdater == null) return;
        dataUpdater.stopUpdater();
        boolean interrupted = Thread.interrupted();
        try {
            dataUpdater.join(2000);
            if (dataUpdater.isAlive()) throw new IllegalStateException("Previous Logger update worker did not stop; refusing overlapping sessions");
        } catch (InterruptedException cancellation) {
            interrupted = true;
            throw new IllegalStateException("Interrupted while stopping Logger data delivery", cancellation);
        } finally { if (interrupted) Thread.currentThread().interrupt(); }
    }

    private void sendEcuQueries(TransmissionManager txManager) {
        final List<EcuQuery> ecuQueries = filterEcuQueries(queryMap.values());
        FileLoggerBinding binding = fileLoggerBinding;
        queriedFileLoggerBinding = settings.isFileLoggingControllerSwitchActive() ? binding : null;
        if (queriedFileLoggerBinding != null) ecuQueries.add(queriedFileLoggerBinding.query);
        txManager.sendQueries(ecuQueries, pollState);
    }

    private void sendExternalQueries() {
        final List<ExternalQuery> externalQueries =
                filterExternalQueries(queryMap.values());
        for (ExternalQuery externalQuery : externalQueries) {
            //FIXME: This is a hack!!
            externalQuery.setResponse(
                    externalQuery.getLoggerData().getSelectedConvertor().convert(null));
        }
    }

    private void endEcuQueries(TransmissionManager txManager) {
        txManager.endQueries();
        pollState.setLastQuery(false);
    }

    private void handleQueryResponse() {
        handleFileLoggerSwitchResponse();
        final Response response = buildResponse(queryMap.values());


        dataUpdater.addResponse(response);
    }

    void handleFileLoggerSwitchResponse() {
        FileLoggerBinding sent = queriedFileLoggerBinding;
        queriedFileLoggerBinding = null;
        if (sent != null && sent == fileLoggerBinding && settings.isFileLoggingControllerSwitchActive())
            sent.monitor.monitorFileLoggerSwitch(sent.query.getResponse());
    }

    private Response buildResponse(Collection<Query> queries) {
        final Response response = new ResponseImpl();
        for (final Query query : queries) {
            response.setDataValue(query.getLoggerData(), query.getResponse());
        }
        return response;
    }

    //FIXME: This is a hack!!
    private List<EcuQuery> filterEcuQueries(Collection<Query> queries) {
        List<EcuQuery> filtered = new ArrayList<EcuQuery>();
        for (Query query : queries) {
            if (EcuQuery.class.isAssignableFrom(query.getClass())) {
                filtered.add((EcuQuery) query);
            }
        }
        return filtered;
    }

    //FIXME: This is a hack!!
    private List<ExternalQuery> filterExternalQueries(Collection<Query> queries) {
        List<ExternalQuery> filtered = new ArrayList<ExternalQuery>();
        for (Query query : queries) {
            if (ExternalQuery.class.isAssignableFrom(query.getClass())) {
                filtered.add((ExternalQuery) query);
            }
        }
        return filtered;
    }

    @Override
    public void stop() {
        stop = true;
        cancelInitialization();
    }

    private void cancelInitialization() {
        InitializationAttempt attempt = activeInitialization.get();
        if (attempt != null) attempt.close();
    }

    private String buildQueryId(String callerId, LoggerData loggerData) {
        return callerId + "\0" + loggerData.getId();
    }

    private synchronized void updateQueryList() {
        removeQueries();
        addQueries();
    }

    private void addQueries() {
        for (String queryId : addList.keySet()) {
            queryMap.put(queryId, addList.get(queryId));
        }
        addList.clear();
    }

    private void removeQueries() {
        for (String queryId : removeList) {
            queryMap.remove(queryId);
        }
        removeList.clear();
    }

    private String buildStatsMessage(long start, int count) {
        String state = MessageFormat.format(
                    rb.getString("SLOWK"), settings.getLoggerProtocol());
        if (pollState.isFastPoll()) {
            state = MessageFormat.format(
                    rb.getString("FASTK"), settings.getLoggerProtocol());
        }
        if (settings.getTransportProtocol().equals("ISO15765")) {
            state = MessageFormat.format(
                    rb.getString("CANBUS"), settings.getLoggerProtocol());
        }
        if (settings.isLogExternalsOnly()) {
            state = MessageFormat.format(
                    rb.getString("EXTERNALS"), settings.getLoggerProtocol());
        }
        double duration = (currentTimeMillis() - start) / 1000.0;
        String result = MessageFormat.format(
                rb.getString("QUERYSTATS"),
                state,
                (count / duration),
                (duration / count)
                );
        return result;
    }

    private void notifyConnecting() {
        notificationExecutor.accept(new Runnable() {
            @Override
            public void run() {
                for (LoggerStatusListener listener : listeners) {
                    listener.connecting();
                }
            }
        });
    }

    private void notifyReading() {
        notificationExecutor.accept(new Runnable() {
            @Override
            public void run() {
                for (LoggerStatusListener listener : listeners) {
                    if(settings.isLogExternalsOnly()) listener.readingDataExternal();
                    else {
                        listener.readingData();
                    }
                }
            }
        });
    }

    private void notifyReconnecting() {
        notificationExecutor.accept(new Runnable() {
            @Override
            public void run() {
                for (LoggerStatusListener listener : listeners) {
                    listener.reconnecting();
                }
            }
        });
    }

    private void notifyStopped() {
        notificationExecutor.accept(new Runnable() {
            @Override
            public void run() {
                for (LoggerStatusListener listener : listeners) {
                    listener.stopped();
                }
            }
        });
    }

}
