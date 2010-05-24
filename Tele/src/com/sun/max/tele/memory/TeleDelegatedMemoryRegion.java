/*
 * Copyright (c) 2007 Sun Microsystems, Inc.  All rights reserved.
 *
 * Sun Microsystems, Inc. has intellectual property rights relating to technology embodied in the product
 * that is described in this document. In particular, and without limitation, these intellectual property
 * rights may include one or more of the U.S. patents listed at http://www.sun.com/patents and one or
 * more additional patents or pending patent applications in the U.S. and in other countries.
 *
 * U.S. Government Rights - Commercial software. Government users are subject to the Sun
 * Microsystems, Inc. standard license agreement and applicable provisions of the FAR and its
 * supplements.
 *
 * Use is subject to license terms. Sun, Sun Microsystems, the Sun logo, Java and Solaris are trademarks or
 * registered trademarks of Sun Microsystems, Inc. in the U.S. and other countries. All SPARC trademarks
 * are used under license and are trademarks or registered trademarks of SPARC International, Inc. in the
 * U.S. and other countries.
 *
 * UNIX is a registered trademark in the U.S. and other countries, exclusively licensed through X/Open
 * Company, Ltd.
 */
package com.sun.max.tele.memory;

import java.lang.management.*;

import com.sun.max.tele.*;
import com.sun.max.tele.object.*;
import com.sun.max.unsafe.*;

/**
 * Representation of a span of memory in the VM where the description of the memory
 * is held by a object in the VM and might change.
 *
 * @author Michael Van De Vanter
 */
public abstract class TeleDelegatedMemoryRegion extends TeleMemoryRegion {

    private final TeleRuntimeMemoryRegion teleRuntimeMemoryRegion;

    protected TeleDelegatedMemoryRegion(TeleVM teleVM, TeleRuntimeMemoryRegion teleRuntimeMemoryRegion) {
        super(teleVM);
        this.teleRuntimeMemoryRegion = teleRuntimeMemoryRegion;
    }

    public final String regionName() {
        return teleRuntimeMemoryRegion.getRegionName();
    }

    public final Address start() {
        return teleRuntimeMemoryRegion.getRegionStart();
    }

    public Size size() {
        return teleRuntimeMemoryRegion.getRegionSize();
    }

    @Override
    public MemoryUsage getUsage() {
        return teleRuntimeMemoryRegion.getUsage();
    }

    @Override
    public boolean containsInAllocated(Address address) {
        return teleRuntimeMemoryRegion.containsInAllocated(address);
    }

}