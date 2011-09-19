/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.max.vm.ext.vma;

import com.sun.max.vm.actor.member.*;

/**
 * The VM executes an (extended) bytecode instruction set and the execution can be subject to advice,
 * specified by the methods below.
 *
 * Auto-generated by {@link BytecodeAdviceGenerator}.
 */

public abstract class BytecodeAdvice {

// START GENERATED CODE
// EDIT AND RUN BytecodeAdviceGenerator.main() TO MODIFY

    public abstract void adviseBeforeConstLoad(Object value);

    public abstract void adviseBeforeConstLoad(long value);

    public abstract void adviseBeforeConstLoad(float value);

    public abstract void adviseBeforeConstLoad(double value);

    public abstract void adviseBeforeLoad(int dispToLocalSlot);

    public abstract void adviseBeforeArrayLoad(Object array, int index);

    public abstract void adviseBeforeStore(int dispToLocalSlot, long value);

    public abstract void adviseBeforeStore(int dispToLocalSlot, float value);

    public abstract void adviseBeforeStore(int dispToLocalSlot, double value);

    public abstract void adviseBeforeStore(int dispToLocalSlot, Object value);

    public abstract void adviseBeforeArrayStore(Object array, int index, long value);

    public abstract void adviseBeforeArrayStore(Object array, int index, float value);

    public abstract void adviseBeforeArrayStore(Object array, int index, double value);

    public abstract void adviseBeforeArrayStore(Object array, int index, Object value);

    public abstract void adviseBeforeStackAdjust(int opcode);

    public abstract void adviseBeforeOperation(int opcode, long op1, long op2);

    public abstract void adviseBeforeOperation(int opcode, float op1, float op2);

    public abstract void adviseBeforeOperation(int opcode, double op1, double op2);

    public abstract void adviseBeforeConversion(int opcode, long op);

    public abstract void adviseBeforeConversion(int opcode, float op);

    public abstract void adviseBeforeConversion(int opcode, double op);

    public abstract void adviseBeforeIf(int opcode, int op1, int op2);

    public abstract void adviseBeforeIf(int opcode, Object op1, Object op2);

    public abstract void adviseBeforeReturn(long value);

    public abstract void adviseBeforeReturn(float value);

    public abstract void adviseBeforeReturn(double value);

    public abstract void adviseBeforeReturn(Object value);

    public abstract void adviseBeforeReturn();

    public abstract void adviseBeforeGetStatic(Object staticTuple, int offset);

    public abstract void adviseBeforePutStatic(Object staticTuple, int offset, double value);

    public abstract void adviseBeforePutStatic(Object staticTuple, int offset, long value);

    public abstract void adviseBeforePutStatic(Object staticTuple, int offset, Object value);

    public abstract void adviseBeforePutStatic(Object staticTuple, int offset, float value);

    public abstract void adviseBeforeGetField(Object object, int offset);

    public abstract void adviseBeforePutField(Object object, int offset, double value);

    public abstract void adviseBeforePutField(Object object, int offset, long value);

    public abstract void adviseBeforePutField(Object object, int offset, Object value);

    public abstract void adviseBeforePutField(Object object, int offset, float value);

    public abstract void adviseBeforeInvokeVirtual(Object object, MethodActor methodActor);

    public abstract void adviseBeforeInvokeSpecial(Object object, MethodActor methodActor);

    public abstract void adviseBeforeInvokeStatic(Object object, MethodActor methodActor);

    public abstract void adviseBeforeInvokeInterface(Object object, MethodActor methodActor);

    public abstract void adviseBeforeArrayLength(Object array, int length);

    public abstract void adviseBeforeThrow(Object object);

    public abstract void adviseBeforeCheckCast(Object object, Object classActor);

    public abstract void adviseBeforeInstanceOf(Object object, Object classActor);

    public abstract void adviseBeforeMonitorEnter(Object object);

    public abstract void adviseBeforeMonitorExit(Object object);

    public abstract void adviseBeforeBytecode(int opcode);

    public abstract void adviseAfterInvokeVirtual(Object object, MethodActor methodActor);

    public abstract void adviseAfterInvokeSpecial(Object object, MethodActor methodActor);

    public abstract void adviseAfterInvokeStatic(Object object, MethodActor methodActor);

    public abstract void adviseAfterInvokeInterface(Object object, MethodActor methodActor);

    public abstract void adviseAfterNew(Object object);

    public abstract void adviseAfterNewArray(Object object, int length);

    public abstract void adviseAfterMultiNewArray(Object object, int[] lengths);

    public abstract void adviseAfterMethodEntry(Object object, MethodActor methodActor);

// END GENERATED CODE
}

