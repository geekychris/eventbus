/*
 * Copyright (c) 2025 Chris Collins chris@hitorro.com
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.eventbus.client;

/**
 * Represents a server address in the EventBus cluster.
 * This class is immutable and thread-safe.
 */
public class ServerAddress {
    private final String host;
    private final int port;
    private final boolean useTls;

    /**
     * Creates a new server address.
     *
     * @param host the hostname or IP address
     * @param port the port number
     * @param useTls whether to use TLS (secure connection)
     */
    public ServerAddress(String host, int port, boolean useTls) {
        this.host = host;
        this.port = port;
        this.useTls = useTls;
    }

    /**
     * Creates a new server address with TLS disabled.
     *
     * @param host the hostname or IP address
     * @param port the port number
     */
    public ServerAddress(String host, int port) {
        this(host, port, false);
    }

    /**
     * Creates a new server address from a string in the format "host:port".
     *
     * @param address the address in "host:port" format
     * @throws IllegalArgumentException if the address format is invalid
     */
    public ServerAddress(String address) {
        String[] parts = address.split(":");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid address format. Expected 'host:port', got '" + address + "'");
        }
        
        this.host = parts[0];
        try {
            this.port = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid port number: " + parts[1], e);
        }
        this.useTls = false;
    }

    /**
     * Returns the host.
     *
     * @return the host
     */
    public String getHost() {
        return host;
    }

    /**
     * Returns the port.
     *
     * @return the port
     */
    public int getPort() {
        return port;
    }

    /**
     * Returns whether TLS is enabled.
     *
     * @return true if TLS is enabled, false otherwise
     */
    public boolean isUseTls() {
        return useTls;
    }

    /**
     * Returns the address in "host:port" format.
     *
     * @return the address in "host:port" format
     */
    @Override
    public String toString() {
        return host + ":" + port;
    }

    /**
     * Returns the gRPC target string for this address.
     *
     * @return the gRPC target string
     */
    public String toTarget() {
        return host + ":" + port;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ServerAddress that = (ServerAddress) o;

        if (port != that.port) return false;
        if (useTls != that.useTls) return false;
        return host.equals(that.host);
    }

    @Override
    public int hashCode() {
        int result = host.hashCode();
        result = 31 * result + port;
        result = 31 * result + (useTls ? 1 : 0);
        return result;
    }
}

