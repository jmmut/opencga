/*
 * Copyright 2015 OpenCB
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.opencb.opencga.core.exec;

import org.apache.tools.ant.types.Commandline;
import org.opencb.opencga.core.common.ListUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class Command extends RunnableProcess {

    // protected String executable;
    // protected String pathScript;
    // protected String outDir;
    // protected Arguments arguments;

    private String commandLine;
    private List<String> environment;
    private Process proc;

    protected static Logger logger = LoggerFactory.getLogger(Command.class);
    private StringBuffer outputBuffer = new StringBuffer();
    private StringBuffer errorBuffer = new StringBuffer();
    private final String[] cmdArray;

    public Command(String commandLine) {
        this.commandLine = commandLine;
        cmdArray = Commandline.translateCommandline(getCommandLine());
    }

    public Command(String commandLine, List<String> environment) {
        this.commandLine = commandLine;
        this.environment = environment;
        cmdArray = Commandline.translateCommandline(getCommandLine());
    }

    public Command(String[] cmdArray, List<String> environment) {
        this.cmdArray = cmdArray;
        this.commandLine = Commandline.toString(cmdArray);
        this.environment = environment;
    }

    @Override
    public void run() {
        try {
            setStatus(Status.RUNNING);

            startTime();
            logger.debug(Commandline.describeCommand(cmdArray));
            if (environment != null && environment.size() > 0) {
                proc = Runtime.getRuntime().exec(cmdArray, ListUtils.toArray(environment));
            } else {
                proc = Runtime.getRuntime().exec(cmdArray);
            }

            InputStream is = proc.getInputStream();
            // Thread out = readOutputStream(is);
            Thread readOutputStreamThread = readOutputStream(is);
            InputStream es = proc.getErrorStream();
            // Thread err = readErrorStream(es);
            Thread readErrorStreamThread = readErrorStream(es);

            proc.waitFor();
            readOutputStreamThread.join();
            readErrorStreamThread.join();
            endTime();

            setExitValue(proc.exitValue());
            if (proc.exitValue() != 0) {
                status = Status.ERROR;
                // output = IOUtils.toString(proc.getInputStream());
                // error = IOUtils.toString(proc.getErrorStream());
                output = outputBuffer.toString();
                error = errorBuffer.toString();
            }
            if (status != Status.KILLED && status != Status.TIMEOUT && status != Status.ERROR) {
                status = Status.DONE;
                // output = IOUtils.toString(proc.getInputStream());
                // error = IOUtils.toString(proc.getErrorStream());
                output = outputBuffer.toString();
                error = errorBuffer.toString();
            }

        } catch (Exception e) {
            exception = e.toString();
            status = Status.ERROR;
            exitValue = -1;
            logger.error("Exception occurred while executing Command {}", exception);
        }
    }

    @Override
    public void destroy() {
        proc.destroy();
    }

    private Thread readOutputStream(InputStream ins) throws IOException {
        final InputStream in = ins;

        Thread T = new Thread("stdout_reader") {
            public void run() {
                try {
                    int bytesRead = 0;
                    int bufferLength = 2048;
                    byte[] buffer = new byte[bufferLength];

                    while (bytesRead != -1) {
                        // int x=in.available();
                        bufferLength = in.available();
                        bufferLength = Math.max(bufferLength, 1);
                        // if (x<=0)
                        // continue ;

                        buffer = new byte[bufferLength];
                        bytesRead = in.read(buffer, 0, bufferLength);
                        if (logger != null) {
                            System.err.print(new String(buffer));
                        }
                        outputBuffer.append(new String(buffer));
                        Thread.sleep(500);
                        logger.debug("stdout - Sleep (last bytesRead = " + bytesRead + ")");
                    }
                    logger.debug("ReadOutputStream - Exit while");
                } catch (Exception ex) {
                    ex.printStackTrace();
                    exception = ex.toString();
                }
            }
        };
        T.start();
        return T;
    }

    private Thread readErrorStream(InputStream ins) throws IOException {
        final InputStream in = ins;

        Thread T = new Thread("stderr_reader") {
            public void run() {

                try {
                    int bytesRead = 0;
                    int bufferLength = 2048;
                    byte[] buffer = new byte[bufferLength];

                    while (bytesRead != -1) {
                        // int x=in.available();
                        // if (x<=0)
                        // continue ;

                        bufferLength = in.available();
                        bufferLength = Math.max(bufferLength, 1);

                        buffer = new byte[bufferLength];
                        bytesRead = in.read(buffer, 0, bufferLength);
                        if (logger != null) {
                            System.err.print(new String(buffer));
                        }
                        errorBuffer.append(new String(buffer));
                        Thread.sleep(500);
                        logger.debug("stderr - Sleep  (last bytesRead = " + bytesRead + ")");
                    }
                    logger.debug("ReadErrorStream - Exit while");
                } catch (Exception ex) {
                    ex.printStackTrace();
                    exception = ex.toString();
                }
            }
        };
        T.start();
        return T;
    }

    /**
     * @param commandLine the commandLine to set
     */
    public void setCommandLine(String commandLine) {
        this.commandLine = commandLine;
    }

    /**
     * @return the commandLine
     */
    public String getCommandLine() {
        return commandLine;
    }

    /**
     * @param environment the environment to set
     */
    public void setEnvironment(List<String> environment) {
        this.environment = environment;
    }

    /**
     * @return the environment
     */
    public List<String> getEnvironment() {
        return environment;
    }

}
