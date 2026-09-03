/*
 * RomRaider Open-Source Tuning, Logging and Reflashing
 * Copyright (C) 2006-2025 RomRaider.com
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

package com.romraider.maps;

import static com.romraider.maps.RomChecksum.calculateRomChecksum;
import static com.romraider.util.HexUtil.asBytes;
import static com.romraider.util.HexUtil.asHex;

import java.io.File;
import java.io.Serializable;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Vector;

import org.apache.log4j.Logger;
import org.w3c.dom.Document;

import com.romraider.activity.ProgressReporter;
import com.romraider.dataflowSimulation.DataflowSimulation;
import com.romraider.maps.checksum.ChecksumManager;
import com.romraider.util.ResourceUtil;
import com.romraider.util.SettingsManager;

public class Rom implements Serializable  {
    private static final long serialVersionUID = 7865405179738828128L;
    private static final Logger LOGGER = Logger.getLogger(Rom.class);
    private static final ResourceBundle rb = new ResourceUtil().getBundle(
            Rom.class.getName());

    private RomID romID;
    private File definitionPath;
    private String fileName = "";
    private File fullFileName = new File(".");
    private byte[] binData;
    private Document doc;
    
    // This is currently only used for unit testing
    // It could however be used to create a list of faulty tables instead
    // of an endless loop or error messages with a bad definition or bin
    private List<String> faultyTables = new LinkedList<String>();

    //This keeps track of DataCells on a byte level
    //This might also be possible to achieve by using the same Data Tables
    protected HashMap<Integer, LinkedList<DataCell>> byteCellMapping = new HashMap<Integer, LinkedList<DataCell>>();
    
    private final LinkedHashMap<String, Table> tableCatalog =
            new LinkedHashMap<String, Table>();
    private final LinkedList<DataflowSimulation> simulations = new LinkedList<DataflowSimulation>();
    private LinkedList<ChecksumManager> checksumManagers = new LinkedList<ChecksumManager>();

    public Rom(RomID romID) {
        this.romID = romID;
    }
    
    public List<String> getFaultyTables()
    {
    	return faultyTables;
    }
    
    public void addTableByName(Table table) {
        table.setRom(this);
        String key = table.getName().toLowerCase();
        tableCatalog.put(key, table);
    }
    
    public void addSimulation(DataflowSimulation sim) {
        simulations.add(sim);
    }
    
    public LinkedList<DataflowSimulation> getSimulations()
    {
    	return this.simulations;
    }
    
    public void removeTableByName(Table table) {
        String key = table.getName().toLowerCase();
        tableCatalog.remove(key);
    }

    public Table getTableByName(String tableName) {
        return tableCatalog.get(tableName.toLowerCase());
    }

    public List<Table> findTables(String regex) {
        List<Table> result = new ArrayList<Table>();
        for (Table table : tableCatalog.values()) {
            String name = table.getName();
            if (name.matches(regex)) result.add(table);
        }
        return result;
    }

    private void handleException(Table table, Exception e, boolean isOutOfBounds)
    {
        boolean isTesting = SettingsManager.getTesting();
        
    	if(!isTesting)
    	{
			String message;
			if(isOutOfBounds) {
                LOGGER.error(table.getName() + " type " + table.getType()
                        + " start " + table.getStorageAddress() + " "
                        + binData.length + " filesize", e);
                message = MessageFormat.format(rb.getString("ADDROUTOFBNDS"),
                        table.getName());
            } else {
                LOGGER.error("Error Populating Table", e);
                message = MessageFormat.format(rb.getString("TABLELOADERR"),
                        table.getName());
            }
            RomUserInteractionService.definitionError(this, table,
                    rb.getString("ECUDEFERROR"), message, e);
    	}
    	else
    	{
    		e.printStackTrace();
    	}
        faultyTables.add(table.getName());
    }
    
    public void populateTables(byte[] binData, ProgressReporter progress) {
        this.binData = binData;
        int size = tableCatalog.size();
        int i = 0;
        faultyTables.clear();

        for(String name: new ArrayList<String>(tableCatalog.keySet())) {
            // update progress
            int currProgress = (int) (i / (double) size * 100);
            progress.update(rb.getString("POPTABLES"), currProgress);

            Table table = tableCatalog.get(name.toLowerCase());
            try {
                if (table.getStorageAddress() >= 0) {
                    try {
                        table.populateTable(this);
                        if (null != table.getName() && table.getName().equalsIgnoreCase("Checksum Fix")){
                            setEditStamp(binData, table.getStorageAddress() - table.getRamOffset());
                        }
                        i++;
                    } catch (ArrayIndexOutOfBoundsException ex) {
                    	handleException(table, ex, true);
                        size--;
                    } catch (IndexOutOfBoundsException iex) {
                    	handleException(table, iex, true);
                        size--;
                    }
                } else {
                    removeTableByName(table);
                    size--;
                }

            } catch (NullPointerException ex) {
            	handleException(table, ex, false);
                size--;
            }
        }

        for(String s: faultyTables) {
            tableCatalog.remove(s.toLowerCase());
        }
    }

    private void setEditStamp(byte[] binData, int address) {
        byte[] stampData = new byte[4];
        System.arraycopy(binData, address+204, stampData, 0, stampData.length);
        String stamp = asHex(stampData);
        if (stamp.equalsIgnoreCase("FFFFFFFF")) {
            romID.setEditStamp("");
        }
        else {
            StringBuilder niceStamp = new StringBuilder(stamp);
            niceStamp.replace(6, 9, String.valueOf(0xFF & stampData[3]));
            niceStamp.insert(6, " v");
            niceStamp.insert(4, "-");
            niceStamp.insert(2, "-");
            niceStamp.insert(0, "20");
            romID.setEditStamp(niceStamp.toString());
        }
    }

    public void setRomID(RomID romID) {
        this.romID = romID;
    }

    public RomID getRomID() {
        return romID;
    }

    public String getRomIDString() {
        return romID.getXmlid();
    }

    public byte[] getBinary() {
        return binData;
    }

    public void setDocument(Document d) {
        this.doc = d;
    }
    public Document getDocument() {
        return this.doc;
    }

    public void setDefinitionPath(File s) {
        definitionPath = s;
    }

    public File getDefinitionPath() {
        return definitionPath;
    }

    @Override
    public String toString() {
        String output = "";
        output = output + "\n---- Rom ----" + romID.toString();
        for(Table table : tableCatalog.values()) {
            output = output + table;
        }
        output = output + "\n---- End Rom ----";

        return output;
    }

    public String getFileName() {
        return fileName;
    }

    public Vector<Table> getTables() {
        Vector<Table> tables = new Vector<Table>(tableCatalog.values());
        Collections.sort(tables);
        return tables;
    }

    /** Tables in definition order without exposing the Swing tree model. */
    public List<Table> getTableCatalog() {
        return Collections.unmodifiableList(
                new ArrayList<Table>(tableCatalog.values()));
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    private void showChecksumFixPopup(Table checksum) {
         if (RomUserInteractionService.confirmChecksumFix(this, checksum,
                 rb.getString("CHECKSUMFIX"),
                 rb.getString("CHKSUMINVALID"))) {
             //TODO: Move to Subaru checksum
             calculateRomChecksum(
                     binData,
                     checksum
             );
         }
    }

    //Most of this function is useless now, since each Datacell is now responsible for each memory region
    //It is only used to correct the Subaru Checksum. Should be moved somewhere else TODO
    public byte[] saveFile() {

        //There can be more than 1 Checksum Fix tables, find them all
        final List<Table> checksumTables = new ArrayList<Table>();
        for (String name: tableCatalog.keySet()) {
            if (name.startsWith("checksum fix")) {
                checksumTables.add(tableCatalog.get(name));
            }
        }

        if (checksumTables.size() == 1) {
            final Table checksum = checksumTables.get(0);
            int binDataPos = checksum.getStorageAddress() -
                             checksum.getRamOffset();
            byte count = binData[binDataPos + 207];
            if (count == -1) {
                count = 1;
            }
            else {
                count++;
            }
            String currentDate = new SimpleDateFormat("yyMMdd").format(new Date());
            String stamp = String.format("%s%02x", currentDate, count);
            byte[] romStamp = asBytes(stamp);
            System.arraycopy(
                    romStamp,
                    0,
                    binData,
                    binDataPos + 204,
                    4);
            setEditStamp(binData, binDataPos);
        }

        for (Table checksum : checksumTables) {
            if (!checksum.isLocked()) {
                //TODO: Move to Subaru checksum
                calculateRomChecksum(
                        binData,
                        checksum
                );
            }
            else if (checksum.isLocked() &&
                    !checksum.isButtonSelected()) {
                    showChecksumFixPopup(checksum);
            }
        }

        updateChecksum();
        return binData;
    }

    public void clearData() {
        clearByteMapping();
        checksumManagers.clear();
        tableCatalog.clear();
        binData = null;
        doc = null;
    }

    public void clearByteMapping() {
        for(List<?> l: byteCellMapping.values())l.clear();

        byteCellMapping.clear();
        byteCellMapping = null;
    }

    public int getRealFileSize() {
        return binData.length;
    }

    public File getFullFileName() {
        return fullFileName;
    }

    public void setFullFileName(File fullFileName) {
        this.fullFileName = fullFileName;
        if (fullFileName != null) this.setFileName(fullFileName.getName());
    }

    public void addChecksumManager(ChecksumManager checksumManager) {
        this.checksumManagers.add(checksumManager);
    }

    public int getNumChecksumsManagers() {
        return checksumManagers.size();
    }

    public int validateChecksum() {
        int correctChecksums = 0;
        boolean valid = true;

        if (!checksumManagers.isEmpty()) {
            for(ChecksumManager cm: checksumManagers) {
                int localCorrectCs = cm.validate(binData);

                if (cm == null || cm.getNumberOfChecksums() != localCorrectCs) {
                    valid = false;
                }
                correctChecksums += localCorrectCs;
            }
        }

        if(!valid) {
            RomUserInteractionService.checksumValidationFailed(this,
                    rb.getString("CHKSUMFAIL"),
                    rb.getString("INVLAIDCHKSUM"));
        }

        return correctChecksums;
    }

    public int updateChecksum() {
        int updatedCs = 0;

        for(ChecksumManager cm: checksumManagers) {
            updatedCs += cm.update(binData);
        }

        RomUserInteractionService.checksumUpdated(this,
                String.format(rb.getString("CHECKSUMFIXED"), updatedCs,
                        getTotalAmountOfChecksums()));

        return updatedCs;
    }

    public int getTotalAmountOfChecksums() {
        int cs = 0;

        for(ChecksumManager cm: checksumManagers) {
            cs += cm.getNumberOfChecksums();
        }

        return cs;
    }
}
