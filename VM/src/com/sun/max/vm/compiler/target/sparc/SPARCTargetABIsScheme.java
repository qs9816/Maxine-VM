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
package com.sun.max.vm.compiler.target.sparc;

import static com.sun.max.asm.sparc.GPR.*;
import static com.sun.max.asm.sparc.FPR.*;

import com.sun.max.annotate.*;
import com.sun.max.asm.sparc.*;
import com.sun.max.collect.*;
import com.sun.max.vm.*;
import com.sun.max.vm.compiler.*;
import com.sun.max.vm.compiler.target.*;
import com.sun.max.vm.stack.sparc.*;

/**
 * @author Laurent Daynes
 */
public abstract class SPARCTargetABIsScheme extends TargetABIsScheme<GPR, FPR> {

    @PROTOTYPE_ONLY
    private static RegisterRoleAssignment<GPR, FPR> nativeRegisterRoleAssignment() {
        return new RegisterRoleAssignment<GPR, FPR>(GPR.class, O6, I6, O6, I6, I0, O0, null, null, null, FPR.class, F0, null, I7, O7);
    }

    @PROTOTYPE_ONLY
    protected static IndexedSequence<GPR> incomingIntegerparameterregisters() {
        return new ArraySequence<GPR>(I0, I1, I2, I3, I4, I5);
    }

    @PROTOTYPE_ONLY
    protected static IndexedSequence<GPR> outgoingIntegerParameterRegisters() {
        return new ArraySequence<GPR>(O0, O1, O2, O3, O4, O5);
    }

    @PROTOTYPE_ONLY
    protected static IndexedSequence<FPR> floatingPointParameterRegisters() {
        return new ArraySequence<FPR>(
                        F0, F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12, F13, F14, F15,
                        F16, F17, F18, F19, F20, F21, F22, F23, F24, F25, F26, F27, F28, F29, F30, F31);
    }

    @PROTOTYPE_ONLY
    public static TargetABI<GPR, FPR> createSPARC64TargetABI(RegisterRoleAssignment<GPR, FPR> registerRoleAssignment, CallEntryPoint callEntryPoint,
                    IndexedSequence<GPR> integerIncomingParameterRegisters,
                    IndexedSequence<GPR> integerOutgoingParameterRegisters,
                    IndexedSequence<FPR> floatingPointParameterRegisters, boolean useRegisterWindows, boolean callPushesReturnAddress) {
        return new TargetABI<GPR, FPR>(registerRoleAssignment, callEntryPoint,
                        integerIncomingParameterRegisters, integerOutgoingParameterRegisters, floatingPointParameterRegisters,
                        useRegisterWindows, callPushesReturnAddress,
                        SPARCStackFrameLayout.STACK_FRAME_ALIGNMENT, SPARCStackFrameLayout.OFFSET_FROM_SP_TO_FIRST_SLOT);
    }

    @PROTOTYPE_ONLY
    public static TargetABI<GPR, FPR> createSPARC64TargetABI(RegisterRoleAssignment<GPR, FPR> registerRoleAssignment, CallEntryPoint callEntryPoint,
                    IndexedSequence<FPR> floatingPointParameterRegisters, boolean useRegisterWindows, boolean callPushesReturnAddress) {
        return createSPARC64TargetABI(
                        registerRoleAssignment,
                        callEntryPoint,
                        incomingIntegerparameterregisters(),
                        outgoingIntegerParameterRegisters(),
                        floatingPointParameterRegisters,
                        useRegisterWindows,
                        callPushesReturnAddress);
    }

    @PROTOTYPE_ONLY
    public SPARCTargetABIsScheme(
                    VMConfiguration vmConfiguration,
                    TargetABI<GPR, FPR> jitABI,
                    TargetABI<GPR, FPR> optimizedJavaABI) {
        super(vmConfiguration,
                        createSPARC64TargetABI(
                                        nativeRegisterRoleAssignment(),
                                        CallEntryPoint.C_ENTRY_POINT,
                                        incomingIntegerparameterregisters(),
                                        outgoingIntegerParameterRegisters(),
                                        null, false, false),
                                        jitABI, optimizedJavaABI, null);
    }
}
