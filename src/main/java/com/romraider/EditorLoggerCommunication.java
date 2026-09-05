/*
 * RomRaider Open-Source Tuning, Logging and Reflashing
 * Copyright (C) 2006-2021 RomRaider.com
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

package com.romraider;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;

public final class EditorLoggerCommunication {

    public enum Exec_type {EDITOR, LOGGER, UNKNOWN};
    
    private static final InetAddress LOOPBACK = InetAddress.getLoopbackAddress();
    private static final int PORT = 23272;
    private static final String ARGUMENT_PREFIX = "RR2ARGS1:";
    private static final int MAX_MESSAGE = 1024 * 1024;
    
    private static Exec_type currentExecType;
    private static String[] currentArgs;
    
    static public class ExecutableInstance {
    	public Exec_type execType;
    	public String[] currentArgs;
    }
     
    public static void setExectable(Exec_type t, String[] a) {
    	currentExecType = t;
    	currentArgs = a;
    }
    
    public static Exec_type getExecutableType() {
    	return currentExecType;
    }
    
    public static String[] getExecutableArgs() {
    	return currentArgs;
    }

    public static boolean isRunning() {
        try {
            ServerSocket sock = new ServerSocket(PORT, 1, LOOPBACK);
            sock.close();
            return false;
        } catch (IOException ex) {
            return true;
        }
    }

    public static ExecutableInstance waitForOtherExec() throws IOException {
        try (ServerSocket sock = new ServerSocket(PORT, 1, LOOPBACK)) {
            return receive(sock);
        }
    }

    static ExecutableInstance receive(ServerSocket sock) throws IOException {
        try (Socket client = sock.accept()) {
            client.setSoTimeout(3000);
            
            BufferedReader br = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
            String type_String = readLine(br);
            String args_String = readLine(br);
            
            ExecutableInstance instance = new ExecutableInstance();
                      
            if(type_String.equalsIgnoreCase(Exec_type.EDITOR.toString())) {
            	instance.execType =  Exec_type.EDITOR;
            }
            else if(type_String.equalsIgnoreCase(Exec_type.LOGGER.toString())) {
            	instance.execType =  Exec_type.LOGGER;
            }
            else {
            	instance.execType =  Exec_type.UNKNOWN;
            }
            
            instance.currentArgs = decodeArguments(args_String);
            return instance;
            
        }
    }
    
    public static void sendTypeToOtherExec(String[] args) {
        try {
            Socket socket = new Socket(LOOPBACK, PORT);
            OutputStream os = socket.getOutputStream();
            
            try {
                PrintWriter pw = new PrintWriter(os, true, StandardCharsets.UTF_8);
                pw.println(getExecutableType().toString());
                
                pw.println(encodeArguments(args));
            } finally {
                socket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static String encodeArguments(String[] args) throws IOException {
        if (args.length > 4096) throw new IOException("Too many launch arguments");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream data = new DataOutputStream(bytes)) {
            data.writeInt(args.length);
            for (String arg : args) {
                byte[] value = arg.getBytes(StandardCharsets.UTF_8);
                if (value.length > MAX_MESSAGE || bytes.size() + value.length > MAX_MESSAGE / 2)
                    throw new IOException("Launch arguments are too long");
                data.writeInt(value.length);
                data.write(value);
            }
        }
        return ARGUMENT_PREFIX + Base64.getEncoder().encodeToString(bytes.toByteArray());
    }

    static String[] decodeArguments(String message) throws IOException {
        if (message == null || message.length() > MAX_MESSAGE) throw new IOException("Invalid launch message");
        // Accept older senders; only the versioned protocol can preserve spaces.
        if (!message.startsWith(ARGUMENT_PREFIX))
            return message.isBlank() ? new String[0] : message.split(" ");
        try (DataInputStream data = new DataInputStream(new ByteArrayInputStream(
                Base64.getDecoder().decode(message.substring(ARGUMENT_PREFIX.length()))))) {
            int count = data.readInt();
            if (count < 0 || count > 4096) throw new IOException("Invalid argument count");
            String[] result = new String[count];
            for (int i = 0; i < count; i++) {
                int length = data.readInt();
                if (length < 0 || length > data.available()) throw new IOException("Invalid argument length");
                result[i] = new String(data.readNBytes(length), StandardCharsets.UTF_8);
            }
            if (data.available() != 0) throw new IOException("Trailing launch data");
            return result;
        } catch (IllegalArgumentException invalid) {
            throw new IOException("Invalid encoded launch arguments", invalid);
        }
    }

    private static String readLine(BufferedReader reader) throws IOException {
        StringBuilder line = new StringBuilder();
        for (int value; (value = reader.read()) != -1;) {
            if (value == '\n') return line.toString();
            if (value != '\r') line.append((char) value);
            if (line.length() > MAX_MESSAGE) throw new IOException("Launch message is too long");
        }
        throw new IOException("Incomplete launch message");
    }
}
