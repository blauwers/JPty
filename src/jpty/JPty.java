/*
 * JPty - A small PTY interface for Java.
 * 
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package jpty;

import java.io.IOException;
import java.util.Arrays;

import jtermios.Termios;

import com.sun.jna.Native;
import com.sun.jna.Platform;

/**
 * Provides access to the pseudoterminal functionality on POSIX(-like) systems, emulating such system calls on non POSIX systems.
 */
public class JPty {
    // INNER TYPES

    public static interface JPtyInterface {
        int execve(String command, String[] argv, String[] env);

        int forkpty(int[] amaster, byte[] name, Termios termp, WinSize winp);

        int getWinSize(int fd, WinSize ws);

        int setWinSize(int fd, WinSize ws);

        int waitpid(int pid, int[] stat, int options);
    }

    // VARIABLES

    private static JPtyInterface m_jpty;

    // METHODS

    static { // INSTANTIATION
        if (Platform.isMac()) {
            m_jpty = new jpty.macosx.JPtyImpl();
        }
        else if (Platform.isLinux()) {
            m_jpty = new jpty.linux.JPtyImpl();
        }
        else if (Platform.isSolaris()) {
            m_jpty = new jpty.solaris.JPtyImpl();
        }
        else {
            throw new RuntimeException("JPty has no support for OS " + System.getProperty("os.name"));
        }
    }

    /**
     * Returns the last known error.
     * 
     * @return the last error number from the native system.
     */
    public static int errno() {
        return Native.getLastError();
    }

    /**
     * Opens a pseudoterminal pair, grants and unlocks access to it, and runs the given command on the slave pseudoterminal.
     * 
     * @return a {@link Pty} instance, never <code>null</code>.
     * @throws IOException in case opening the pseudoterminal failed.
     */
    public static Pty execInPTY(String command, String[] arguments)
        throws IOException {
        return execInPTY(command, arguments, null);
    }

    /**
     * Opens a pseudoterminal pair, grants and unlocks access to it, and runs the given command on the slave pseudoterminal.
     * 
     * @return a {@link Pty} instance, never <code>null</code>.
     * @throws IOException in case opening the pseudoterminal failed.
     */
    public static Pty execInPTY(String command, String[] arguments,
        String[] environment) throws IOException {
        return execInPTY(command, arguments, environment, null, null);
    }

    /**
     * Opens a pseudoterminal pair, grants and unlocks access to it, and runs the given command on the slave pseudoterminal.
     * 
     * @param termios the initial termios structure, may be <code>null</code>;
     * @param winsize the initial winsize structure, may be <code>null</code>;
     * @param commandLine the command line to execute.
     * @return a {@link Pty} instance, never <code>null</code>.
     * @throws IOException in case opening the pseudoterminal failed.
     */
    public static Pty execInPTY(String command, String[] arguments, String[] environment, Termios termios, WinSize winsize) throws IOException {
        if (command == null) {
            throw new IllegalArgumentException("Invalid command line!");
        }

        int[] master = new int[] { -1 };
        byte[] name = new byte[1024];
        int pid;

        if ((pid = m_jpty.forkpty(master, name, termios, winsize)) < 0) {
            throw new IOException("Failed to open PTY!");
        }

        if (pid == 0) {
            // Child...
            final String[] argv = processArgv(command, arguments);

            // Replaces this child process with the given command(line)...
            execve(command, argv, environment);

            // Actually, we should never come here if the command execution went OK...
            throw new RuntimeException("Child failed with error: " + errno());
        }

        // Just a safety measure...
        if (master[0] < 0) {
            throw new RuntimeException("Failed to fork PTY!");
        }

        return new Pty(master[0], pid);
    }

    /**
     * Blocks and waits until the given PID either terminates, or receives a signal.
     * 
     * @param pid the process ID to wait for;
     * @param stat an array of 1 integer in which the status of the process is stored;
     * @param options the bit mask with options.
     */
    public static int waitpid(int pid, int[] stat, int options) {
        return m_jpty.waitpid(pid, stat, options);
    }

    /**
     * Reports the window size for the given file descriptor.
     * 
     * @param fd the file descriptor to report the window size for;
     * @param ws the window size to place the results in.
     * @return 0 upon success, or -1 upon failure.
     */
    public static int getWinSize(int fd, WinSize ws) {
        return m_jpty.getWinSize(fd, ws);
    }

    /**
     * Sets the window size for the given file descriptor.
     * 
     * @param fd the file descriptor to set the window size for;
     * @param ws the new window size to set.
     * @return 0 upon success, or -1 upon failure.
     */
    public static int setWinSize(int fd, WinSize ws) {
        return m_jpty.setWinSize(fd, ws);
    }

    /**
     * Not public as this method <em>replaces</em> the current process and therefor should be used with caution.
     * 
     * @param command the command to execute.
     */
    private static int execve(String command, String[] argv, String[] env) {
        return m_jpty.execve(command, argv, env);
    }

    /**
     * Processes the given command + arguments and crafts a valid array of arguments as expected by {@link #execve(String, String[], String[])}.
     * 
     * @param command the command to run, cannot be <code>null</code>;
     * @param arguments the command line arguments, can be <code>null</code>.
     * @return a new arguments array, never <code>null</code>.
     */
    private static String[] processArgv(String command, String[] arguments) {
        final String[] argv;
        if (arguments == null) {
            argv = new String[] { command };
        }
        else {
            if (!command.equals(arguments[0])) {
                argv = new String[arguments.length + 1];
                argv[0] = command;
                System.arraycopy(arguments, 0, argv, 1, arguments.length);
            }
            else {
                argv = Arrays.copyOf(arguments, arguments.length);
            }
        }
        return argv;
    }
}