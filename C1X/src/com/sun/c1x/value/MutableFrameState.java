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
package com.sun.c1x.value;

import java.util.*;

import com.sun.c1x.ir.*;
import com.sun.c1x.util.*;
import com.sun.cri.ci.*;


/**
 * The {@code MutableFrameState} class extends abstract {@link FrameState} with methods modifying the frame state.
 * Only {@code MutableFrameState} can be instantiated and thus the object references in which it is stored decide
 * whether it is used as immutable or not. Thus and because they can be shared at different places in the compiler,
 * {@link FrameState}s must not be cast to {@code MutableFrameState}s. Instead, a new copy must be created using
 * {@link FrameState#copy()}.
 * Contrariwise and as an optimization, an instance referenced as {@code MutableFrameState} can be assigned to
 * a variable, field, or method parameter of type {@link FrameState} without creating an immutable copy before
 * (using {@link #immutableCopy()}) if the state is not mutated after the assignment.
 *
 * @author Michael Duller
 */
public class MutableFrameState extends FrameState {

    public MutableFrameState(IRScope irScope, int maxLocals, int maxStack) {
        super(irScope, maxLocals, maxStack);
    }

    /**
     * Replace the local variables in this frame state with the local variables from the specified frame state. This is
     * used in inlining.
     *
     * @param with the frame state containing the new local variables
     */
    public void replaceLocals(FrameState with) {
        assert with.maxLocals == maxLocals;
        System.arraycopy(with.values, 0, values, 0, maxLocals);
    }

    /**
     * Replace the stack in this frame state with the stack from the specified frame state. This is used in inlining.
     *
     * @param with the frame state containing the new local variables
     */
    public void replaceStack(FrameState with) {
        System.arraycopy(with.values, with.maxLocals, values, maxLocals, with.stackIndex);
        stackIndex = with.stackIndex;
    }

    /**
     * Replace the locks in this frame state with the locks from the specified frame state. This is used in inlining.
     *
     * @param with the frame state containing the new local variables
     */
    public void replaceLocks(FrameState with) {
        if (with.locks == null) {
            locks = null;
        } else {
            locks = Util.uncheckedCast(with.locks.clone());
        }
    }

    /**
     * Clears all values on this stack.
     */
    public void clearStack() {
        stackIndex = 0;
    }

    public void clearLocals() {
        for (int i = 0; i < values.length; i++) {
            values[i] = null;
        }
    }

    /**
     * Truncates this stack to the specified size.
     * @param size the size to truncate to
     */
    public void truncateStack(int size) {
        stackIndex = size;
    }

    /**
     * Pushes an instruction onto the stack with the expected type.
     * @param kind the type expected for this instruction
     * @param x the instruction to push onto the stack
     */
    public void push(CiKind kind, Value x) {
        assert kind != CiKind.Void;
        xpush(assertKind(kind, x));
        if (kind.sizeInSlots() == 2) {
            xpush(null);
        }
        if (kind.isWord()) {
            unsafe = true;
        }
    }

    /**
     * Pushes a value onto the stack without checking the type.
     * @param x the instruction to push onto the stack
     */
    public void xpush(Value x) {
        assert stackIndex >= 0;
        assert maxLocals + stackIndex < values.length;
        values[maxLocals + stackIndex++] = x;
    }

    /**
     * Pushes a value onto the stack and checks that it is an int.
     * @param x the instruction to push onto the stack
     */
    public void ipush(Value x) {
        xpush(assertInt(x));
    }

    /**
     * Pushes a value onto the stack and checks that it is a float.
     * @param x the instruction to push onto the stack
     */
    public void fpush(Value x) {
        xpush(assertFloat(x));
    }

    /**
     * Pushes a value onto the stack and checks that it is an object.
     * @param x the instruction to push onto the stack
     */
    public void apush(Value x) {
        xpush(assertObject(x));
    }

    /**
     * Pushes a value onto the stack and checks that it is a word.
     * @param x the instruction to push onto the stack
     */
    public void wpush(Value x) {
        unsafe = true;
        xpush(assertWord(x));
    }

    /**
     * Pushes a value onto the stack and checks that it is a JSR return address.
     * @param x the instruction to push onto the stack
     */
    public void jpush(Value x) {
        xpush(assertJsr(x));
    }

    /**
     * Pushes a value onto the stack and checks that it is a long.
     *
     * @param x the instruction to push onto the stack
     */
    public void lpush(Value x) {
        xpush(assertLong(x));
        xpush(null);
    }

    /**
     * Pushes a value onto the stack and checks that it is a double.
     * @param x the instruction to push onto the stack
     */
    public void dpush(Value x) {
        xpush(assertDouble(x));
        xpush(null);
    }

    /**
     * Pops an instruction off the stack with the expected type.
     * @param kind the expected type
     * @return the instruction on the top of the stack
     */
    public Value pop(CiKind kind) {
        if (kind.sizeInSlots() == 2) {
            xpop();
        }
        return assertKind(kind, xpop());
    }

    /**
     * Pops a value off of the stack without checking the type.
     * @return x the instruction popped off the stack
     */
    public Value xpop() {
        assert stackIndex >= 1;
        return values[maxLocals + --stackIndex];
    }

    /**
     * Pops a value off of the stack and checks that it is an int.
     * @return x the instruction popped off the stack
     */
    public Value ipop() {
        return assertInt(xpop());
    }

    /**
     * Pops a value off of the stack and checks that it is a float.
     * @return x the instruction popped off the stack
     */
    public Value fpop() {
        return assertFloat(xpop());
    }

    /**
     * Pops a value off of the stack and checks that it is an object.
     * @return x the instruction popped off the stack
     */
    public Value apop() {
        return assertObject(xpop());
    }

    /**
     * Pops a value off of the stack and checks that it is a word.
     * @return x the instruction popped off the stack
     */
    public Value wpop() {
        return assertWord(xpop());
    }

    /**
     * Pops a value off of the stack and checks that it is a JSR return address.
     * @return x the instruction popped off the stack
     */
    public Value jpop() {
        return assertJsr(xpop());
    }

    /**
     * Pops a value off of the stack and checks that it is a long.
     * @return x the instruction popped off the stack
     */
    public Value lpop() {
        assertHigh(xpop());
        return assertLong(xpop());
    }

    /**
     * Pops a value off of the stack and checks that it is a double.
     * @return x the instruction popped off the stack
     */
    public Value dpop() {
        assertHigh(xpop());
        return assertDouble(xpop());
    }

    private Value assertKind(CiKind kind, Value x) {
        assert x != null && (unsafe || x.kind == kind) : "kind=" + kind + ", value=" + x + ((x == null) ? "" : ", value.kind=" + x.kind);
        return x;
    }

    private Value assertLong(Value x) {
        assert x != null && (unsafe || x.kind == CiKind.Long);
        return x;
    }

    private Value assertJsr(Value x) {
        assert x != null && (unsafe || x.kind == CiKind.Jsr);
        return x;
    }

    private Value assertInt(Value x) {
        assert x != null && (unsafe || x.kind == CiKind.Int);
        return x;
    }

    private Value assertFloat(Value x) {
        assert x != null && (unsafe || x.kind == CiKind.Float);
        return x;
    }

    private Value assertObject(Value x) {
        assert x != null && (unsafe || x.kind == CiKind.Object);
        return x;
    }

    private Value assertWord(Value x) {
        assert x != null && (unsafe || x.kind == CiKind.Word);
        return x;
    }

    private Value assertDouble(Value x) {
        assert x != null && (unsafe || x.kind == CiKind.Double);
        return x;
    }

    /**
     * Pop the specified number of slots off of this stack and return them as an array of instructions.
     * @param size the number of arguments off of the stack
     * @return an array containing the arguments off of the stack
     */
    public Value[] popArguments(int size) {
        int base = stackIndex - size;
        Value[] r = new Value[size];
        int y = maxLocals + base;
        for (int i = 0; i < size; ++i) {
            assert values[y] != null || values[y - 1].kind.jvmSlots == 2;
            r[i] = values[y++];
        }
        stackIndex = base;
        return r;
    }

    /**
     * Locks a new object within the specified IRScope.
     * @param scope the IRScope in which this locking operation occurs
     * @param obj the object being locked
     */
    public void lock(IRScope scope, Value obj, int totalNumberOfLocks) {
        if (locks == null) {
            locks = new ArrayList<Value>();
        }
        locks.add(obj);
        scope.setMinimumNumberOfLocks(totalNumberOfLocks);
    }

    /**
     * Unlock the lock on the top of the stack.
     */
    public void unlock() {
        locks.remove(locks.size() - 1);
    }

    /**
     * Gets an immutable copy of this state.
     */
    public FrameState immutableCopy() {
        return copy(true, true, true);
    }

    private static void assertHigh(Value x) {
        assert x == null;
    }

}