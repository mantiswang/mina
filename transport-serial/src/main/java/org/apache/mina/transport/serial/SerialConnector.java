/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */
package org.apache.mina.transport.serial;

import gnu.io.CommPortIdentifier;
import gnu.io.PortInUseException;
import gnu.io.SerialPort;
import gnu.io.UnsupportedCommOperationException;

import java.io.IOException;
import java.net.SocketAddress;
import java.util.Enumeration;
import java.util.TooManyListenersException;
import java.util.concurrent.Executor;

import org.apache.mina.common.AbstractIoConnector;
import org.apache.mina.common.ConnectFuture;
import org.apache.mina.common.DefaultConnectFuture;
import org.apache.mina.common.IdleStatusChecker;
import org.apache.mina.common.IoConnector;
import org.apache.mina.common.IoFuture;
import org.apache.mina.common.IoSessionInitializer;
import org.apache.mina.common.TransportMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link IoConnector} for serial communication transport.
 *
 * @author The Apache MINA Project (dev@mina.apache.org)
 * @version $Rev: 529576 $, $Date: 2007-04-17 14:25:07 +0200 (mar., 17 avr. 2007) $
 */
public final class SerialConnector extends AbstractIoConnector {
    private final Logger log;

    public SerialConnector() {
        this(null);
    }

    public SerialConnector(Executor executor) {
        super(new DefaultSerialSessionConfig(), executor);
        log = LoggerFactory.getLogger(SerialConnector.class);
    }

    @Override
    protected ConnectFuture connect0(
            SocketAddress remoteAddress, SocketAddress localAddress,
            IoSessionInitializer<? extends ConnectFuture> sessionInitializer) {

        CommPortIdentifier portId;
        Enumeration<?> portList = CommPortIdentifier.getPortIdentifiers();

        SerialAddress portAddress = (SerialAddress) remoteAddress;

        // looping around found ports
        while (portList.hasMoreElements()) {
            portId = (CommPortIdentifier) portList.nextElement();
            if (portId.getPortType() == CommPortIdentifier.PORT_SERIAL) {
                if (log.isDebugEnabled()) {
                    log.debug("Serial port discovered : " + portId.getName());
                }
                if (portId.getName().equals(portAddress.getName())) {
                    try {
                        if (log.isDebugEnabled()) {
                            log
                                    .debug("Serial port found : "
                                            + portId.getName());
                        }

                        SerialPort serialPort = initializePort("Apache MINA",
                                portId, portAddress);

                        ConnectFuture future = new DefaultConnectFuture();
                        SerialSessionImpl session = new SerialSessionImpl(
                                this, getListeners(), portAddress, serialPort);
                        finishSessionInitialization(session, future, sessionInitializer);
                        session.start();
                        return future;
                    } catch (PortInUseException e) {
                        if (log.isDebugEnabled()) {
                            log.debug("Port In Use Exception : ", e);
                        }
                        return DefaultConnectFuture.newFailedFuture(e);
                    } catch (UnsupportedCommOperationException e) {
                        if (log.isDebugEnabled()) {
                            log.debug("Comm Exception : ", e);
                        }
                        return DefaultConnectFuture.newFailedFuture(e);
                    } catch (IOException e) {
                        if (log.isDebugEnabled()) {
                            log.debug("IOException : ", e);
                        }
                        return DefaultConnectFuture.newFailedFuture(e);
                    } catch (TooManyListenersException e) {
                        if (log.isDebugEnabled()) {
                            log.debug("TooManyListenersException : ", e);
                        }
                        return DefaultConnectFuture.newFailedFuture(e);
                    }
                }
            }
        }

        return DefaultConnectFuture
                .newFailedFuture(new SerialPortUnavailableException(
                        "Serial port not found"));
    }

    @Override
    protected IoFuture dispose0() throws Exception {
        return null;
    }

    public TransportMetadata getTransportMetadata() {
        return SerialSessionImpl.METADATA;
    }

    private SerialPort initializePort(String user, CommPortIdentifier portId,
            SerialAddress portAddress)
            throws UnsupportedCommOperationException, PortInUseException {

        SerialSessionConfig config = (SerialSessionConfig) getSessionConfig();

        long connectTimeout = getConnectTimeoutMillis();
        if (connectTimeout > Integer.MAX_VALUE) {
            connectTimeout = Integer.MAX_VALUE;
        }

        SerialPort serialPort = (SerialPort) portId.open(
                user, (int) connectTimeout);

        serialPort.setSerialPortParams(portAddress.getBauds(), portAddress
                .getDataBitsForRXTX(), portAddress.getStopBitsForRXTX(),
                portAddress.getParityForRXTX());

        serialPort.setFlowControlMode(portAddress.getFLowControlForRXTX());

        serialPort.notifyOnDataAvailable(true);

        if (config.isLowLatency()) {
            serialPort.setLowLatency();
        }

        serialPort.setInputBufferSize(config.getInputBufferSize());

        if (config.getReceiveThreshold() >= 0) {
            serialPort.enableReceiveThreshold(config.getReceiveThreshold());
        } else {
            serialPort.disableReceiveThreshold();
        }

        return serialPort;
    }

    IdleStatusChecker getIdleStatusChecker0() {
        return super.getIdleStatusChecker();
    }
}
