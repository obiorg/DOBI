/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Exception.java to edit this template
 */
package org.dobi.moka7.Exceptions;

import java.io.IOException;

/**
 *
 * @author r.hendrick
 */
public class S7Exception extends IOException {

    @java.io.Serial
    static final long serialVersionUID = 7818375828146090156L;

    /**
     * Creates a new instance of <code>S7Exceptions</code> without detail
     * message.
     */
    public S7Exception(int errTCPConnectionFailed, IOException ioException) {
        super();
    }

    /**
     * Constructs an instance of <code>S7Exceptions</code> with the specified
     * detail message.
     *
     * @param msg the detail message.
     */
    public S7Exception(String msg) {
        super(msg);
    }

}
