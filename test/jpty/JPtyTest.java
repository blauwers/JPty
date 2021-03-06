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


import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import junit.framework.TestCase;

import com.sun.jna.Platform;


/**
 * Test cases for {@link JPty}.
 */
public class JPtyTest extends TestCase
{
  static class Command
  {
    final String m_command;
    final String[] m_args;

    public Command( String command, String[] args )
    {
      m_command = command;
      m_args = args;
    }
  }

  /**
   * Remove the 'interactive' prefix to run an interactive bash console.
   */
  public void interactiveTestRunConsoleOk() throws Exception
  {
    final CountDownLatch latch = new CountDownLatch( 1 );

    Command cmd = new Command( "/bin/bash", new String[] { "-i" } );

    // Start the process in a PTY...
    final Pty pty = JPty.execInPTY( cmd.m_command, cmd.m_args );

    // Asynchronously check whether the output of the process is captured
    // properly...
    Thread t1 = new Thread()
    {
      public void run()
      {
        InputStream is = pty.getInputStream();

        try
        {
          int ch;
          while ( pty.isChildAlive() && ( ch = is.read() ) >= 0 )
          {
            if ( ch >= 0 )
            {
              System.out.write( ch );
            }
          }
          
          latch.countDown();
        }
        catch ( Exception e )
        {
          e.printStackTrace();
        }
      }
    };
    t1.start();

    // Asynchronously wait for a little while, then close the PTY, which should
    // force our child process to be terminated...
    Thread t2 = new Thread()
    {
      public void run()
      {
        OutputStream os = pty.getOutputStream();

        try
        {
          int ch;
          while ( pty.isChildAlive() && ( ch = System.in.read() ) >= 0 )
          {
            if ( ch >= 0 )
            {
              os.write( ch );
            }
          }
        }
        catch ( Exception e )
        {
          e.printStackTrace();
        }
      }
    };
    t2.start();

    assertTrue( latch.await( 600, TimeUnit.SECONDS ) );
    // We should've waited long enough to have read some of the input...

    int result = pty.waitFor();

    t1.join();
    t2.join();

    assertTrue( "Unexpected process result: " + result, -1 == result );
  }

  /**
   * Tests that closing the Pty after the child process is finished works
   * normally. Should keep track of issue #1.
   */
  public void testClosePtyForTerminatedChildOk() throws Exception
  {
    final CountDownLatch latch = new CountDownLatch( 1 );

    Command cmd = preparePingCommand( 2 );

    // Start the process in a PTY...
    final Pty pty = JPty.execInPTY( cmd.m_command, cmd.m_args );
    final int[] result = { -1 };

    final AtomicInteger readChars = new AtomicInteger();

    // Asynchronously check whether the output of the process is captured
    // properly...
    Thread t1 = new Thread()
    {
      public void run()
      {
        InputStream is = pty.getInputStream();

        try
        {
          int ch;
          while ( pty.isChildAlive() && ( ch = is.read() ) >= 0 )
          {
            if ( ch >= 0 )
            {
              readChars.incrementAndGet();
            }
          }
        }
        catch ( Exception e )
        {
          e.printStackTrace();
        }
      }
    };
    t1.start();

    // Asynchronously wait for a little while, then close the PTY, but our child
    // process should be terminated already...
    Thread t2 = new Thread()
    {
      public void run()
      {
        try
        {
          TimeUnit.MILLISECONDS.sleep( 2500L );

          pty.close();

          result[0] = pty.waitFor();

          latch.countDown();
        }
        catch ( Exception e )
        {
          e.printStackTrace();
        }
      }
    };
    t2.start();

    assertTrue( latch.await( 10, TimeUnit.SECONDS ) );
    // We should've waited long enough to have read some of the input...
    assertTrue( readChars.get() > 0 );

    t1.join();
    t2.join();

    assertTrue( "Unexpected process result: " + result[0], -1 == result[0] );
  }

  /**
   * Tests that the child process is terminated if the {@link Pty} closed before
   * the child process is finished. Should keep track of issue #1.
   */
  public void testClosePtyTerminatesChildOk() throws Exception
  {
    final CountDownLatch latch = new CountDownLatch( 1 );

    Command cmd = preparePingCommand( 15 );

    // Start the process in a PTY...
    final Pty pty = JPty.execInPTY( cmd.m_command, cmd.m_args );
    final int[] result = { -1 };

    final AtomicInteger readChars = new AtomicInteger();

    // Asynchronously check whether the output of the process is captured
    // properly...
    Thread t1 = new Thread()
    {
      public void run()
      {
        InputStream is = pty.getInputStream();

        try
        {
          int ch;
          while ( pty.isChildAlive() && ( ch = is.read() ) >= 0 )
          {
            if ( ch >= 0 )
            {
              readChars.incrementAndGet();
            }
          }
        }
        catch ( Exception e )
        {
          e.printStackTrace();
        }
      }
    };
    t1.start();

    // Asynchronously wait for a little while, then close the PTY, which should
    // force our child process to be terminated...
    Thread t2 = new Thread()
    {
      public void run()
      {
        try
        {
          TimeUnit.MILLISECONDS.sleep( 500L );

          pty.close();

          result[0] = pty.waitFor();

          latch.countDown();
        }
        catch ( Exception e )
        {
          e.printStackTrace();
        }
      }
    };
    t2.start();

    assertTrue( latch.await( 10, TimeUnit.SECONDS ) );
    // We should've waited long enough to have read some of the input...
    assertTrue( readChars.get() > 0 );

    t1.join();
    t2.join();

    assertTrue( "Unexpected process result: " + result[0], -1 == result[0] );
  }

  /**
   * Tests that we can execute a process in a PTY, and wait for its normal
   * termination.
   */
  public void testExecInPTY() throws Exception
  {
    final CountDownLatch latch = new CountDownLatch( 1 );

    Command cmd = preparePingCommand( 2 );

    // Start the process in a PTY...
    final Pty pty = JPty.execInPTY( cmd.m_command, cmd.m_args );
    final int[] result = { -1 };

    // Asynchronously wait for the process to end...
    Thread t = new Thread()
    {
      public void run()
      {
        try
        {
          result[0] = pty.waitFor();

          latch.countDown();
        }
        catch ( InterruptedException e )
        {
          // Simply stop the thread...
        }
      }
    };
    t.start();

    assertTrue( "Child already terminated?!", pty.isChildAlive() );

    assertTrue( latch.await( 10, TimeUnit.SECONDS ) );

    t.join();

    assertEquals( "Unexpected process result!", 0, result[0] );
  }

  /**
   * Tests that getting and setting the window size for a file descriptor works.
   */
  public void testGetAndSetWinSize() throws Exception
  {
    Command cmd = preparePingCommand( 2 );

    Pty pty = JPty.execInPTY( cmd.m_command, cmd.m_args );

    WinSize ws = new WinSize();
    ws.ws_col = 120;
    ws.ws_row = 30;
    pty.setWinSize( ws );

    WinSize ws1 = pty.getWinSize();

    assertNotNull( ws1 );
    assertEquals( 120, ws1.ws_col );
    assertEquals( 30, ws1.ws_row );

    pty.waitFor();
  }

  private Command preparePingCommand( int count )
  {
    String value = Integer.toString( count );
    if ( Platform.isWindows() )
    {
      return new Command( "ping", new String[] { "-n", value, "127.0.0.1" } );
    }
    else if ( Platform.isSolaris() )
    {
      return new Command( "/usr/sbin/ping", new String[] { "-s", "127.0.0.1", "64", value } );
    }
    else if ( Platform.isMac() || Platform.isFreeBSD() )
    {
      return new Command( "/sbin/ping", new String[] { "-c", value, "127.0.0.1" } );
    }
    else if ( Platform.isLinux() )
    {
      return new Command( "/bin/ping", new String[] { "-c", value, "127.0.0.1" } );
    }

    throw new RuntimeException( "Unsupported platform!" );
  }
}
