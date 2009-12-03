/*
 * Copyright (c) 2009 Sun Microsystems, Inc.  All rights reserved.
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
package com.sun.c1x.ci;

/**
 * This class represents a register location. It can be used to represent either a single
 * register or a pair of registers.
 *
 * @author Ben L. Titzer
 */
public final class CiRegisterLocation extends CiLocation {

    public final CiRegister first;
    public final CiRegister second;

    public CiRegisterLocation(CiKind kind, CiRegister first, CiRegister second) {
        super(kind);
        assert kind.size == 2;
        this.first = first;
        this.second = second;
    }

    public CiRegisterLocation(CiKind kind, CiRegister first) {
        super(kind);
        assert kind.size == 1;
        this.first = first;
        this.second = null;
    }

    public int hashCode() {
        return kind.ordinal() + first.hashCode(); // second's hashcode probably doesn't help much
    }

    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (o instanceof CiRegisterLocation) {
            CiRegisterLocation l = (CiRegisterLocation) o;
            return l.kind == kind && l.first == first && l.second == second;
        }
        return false;
    }

    public String toString() {
        return "%" + first.name + (second == null ? "" : "&" + second.name) + ":" + kind;
    }
}