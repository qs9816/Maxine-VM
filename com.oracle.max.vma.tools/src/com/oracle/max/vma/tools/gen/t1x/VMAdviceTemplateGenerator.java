/*
 * Copyright (c) 2011, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.vma.tools.gen.t1x;

import static com.oracle.max.vma.tools.gen.vma.AdviceGeneratorHelper.*;
import static com.sun.max.vm.t1x.T1XTemplateTag.*;

import java.io.*;
import java.util.*;

import com.oracle.max.vm.ext.vma.*;
import com.sun.max.annotate.*;
import com.sun.max.program.*;
import com.sun.max.vm.actor.member.*;
import com.sun.max.vm.t1x.*;

/**
 * Template generation that supports the {@link VMAdviceHandler} interface.
 * Note that not all templates support both before/after advice but every one supports
 * at least one of them. Accordingly, this program can generate several output variants
 * so as to provide runtime flexibility and avoid unnecessary duplication:
 * <ul>
 * <li>All templates generated with whatever before/advice is available. This essentially parallels what is
 * produced by {@link T1XTemplateGenerator} in normal mode.
 * <li>Only the templates that provide before advice, with only before advice generated.
 * <li>Only the templates that provide after advice, with only after advice generated.
 * <li>Only the templates that provide before and after advice, with before and advice generated.
 * </ul>
 *
 * Unfortunately, we cannot reuse the standard templates for INVOKE as we need consistent access to the
 * {@link MethodActor} to pass to the advice handler. So we override the standard definitions in this class.
 *
 * Two different mechanisms can be generated for testing whether the advice methods should be invoked.
 * All ultimately test bit zero of the {@klink VMAJavaRunScheme#VM_ADVISING} thread local.
 * The first mechanism is to include the body of {@link VMAJavaRunScheme#isAdvising} which is an
 * {@code INLINE} method that tests the bit using standard Java. The reason we don't just rely on the
 * inlining mechanism is that the generated code is less efficient than the manually inlined version
 * owing to a C1X limitation.
 *
 * The second, and default, mechanism uses an {@code INTRINSIC} method that tests the bit explicitly
 * and branches directly on its value. This is preferable because it avoids the use of a register to
 * load the value for a standard comparison .
 */
@HOSTED_ONLY
public class VMAdviceTemplateGenerator extends T1XTemplateGenerator {

    /**
     * This records the advice capability for each {@link T1XTemplateTag}.
     */
    private static boolean[][] tagAdviceCapabilities = new boolean[T1XTemplateTag.values().length][AdviceType.values().length];

    private static class DiscoverCapabilitiesHook implements AdviceHook {

        public void startMethodGeneration() {
        }

        public void generate(T1XTemplateTag tag, AdviceType at, String... args) {
            tagAdviceCapabilities[tag.ordinal()][at.ordinal()] = true;
        }
    }

    private static class MethodStatus {
        int offset; // in the ByteArrayOutputStream array
        boolean output; // are we outputting this method
        T1XTemplateTag tag; // that this method is associated with
        MethodStatus(int offset) {
            this.offset = offset;
            this.output = true;
        }
    }

    private static class ThisByteArrayOutputStream extends ByteArrayOutputStream {
        ArrayList<MethodStatus> methodStatusList = new ArrayList<MethodStatus>();
        void writeOut() {
            int listLength = methodStatusList.size();
            for (int i = 0; i < listLength; i++) {
                MethodStatus methodStatus = methodStatusList.get(i);
                if (methodStatus.output) {
                    System.out.write(buf, methodStatus.offset, (i == listLength - 1 ? size() : methodStatusList.get(i + 1).offset) - methodStatus.offset);
                }
            }
        }

        void addMethodStatus() {
            methodStatusList.add(new MethodStatus(size()));
        }

        /**
         *  Discard this and any intervening untagged (helper) methods.
         * @param tag
         */
        void discard(T1XTemplateTag tag) {
            final int listLength  = methodStatusList.size();
            //Checkstyle: stop Indentation check
            methodStatusList.get(listLength - 1).output = false;
            //Checkstyle: resume Indentation check
            int index = listLength - 2;
            while (index >= 0) {
                MethodStatus ms = methodStatusList.get(index);
                if (ms.tag == null) {
                    ms.output = false;
                } else if (ms.tag != tag) {
                    return;
                }
                index--;
            }
        }

        void setTag(T1XTemplateTag tag) {
            //Checkstyle: stop Indentation check
            methodStatusList.get(methodStatusList.size() - 1).tag = tag;
            //Checkstyle: resume Indentation check

        }
    }

    private static final String METHOD_PREFIX = "            VMAStaticBytecodeAdvice.advise%s%s";
    private static AdviceType adviceType;
    private static String methodName;
    private static ThisByteArrayOutputStream byteArrayOut;

    private static final String ISADVISING = "VMAJavaRunScheme.isAdvising()";
    /**
     * We explicitly inline the above as the automatic inlining currently generates sub-optimal code.
     */
    private static final String INLINE_ISADVISING = "VmThread.currentTLA().getWord(VMAJavaRunScheme.VM_ADVISING.index) != Word.zero()";
    /**
     * This is the version that gets optimal code via the BT instruction.
     */
    private static final String ALT_ISADVISING = "Intrinsics.readLatchBit(VMAJavaRunScheme.VM_ADVISING.offset, 0)";
    /**
     * If {@code true}, generate bt instruction variant of the advice guard.
     */
    private static boolean bitGuard = true;

    /**
     * In this mode we generate all templates with whatever advice support they have.
     */
    private static boolean generateAll;
    /**
     * Records whether we want to generate a particular advice type, when {@link #generateAll} is {@code false}.
     */
    private static boolean[] generating = new boolean[AdviceType.values().length];

    private static boolean hasBeforeAndAfter(T1XTemplateTag tag) {
        for (AdviceType at : AdviceType.values()) {
            if (!tagAdviceCapabilities[tag.ordinal()][at.ordinal()]) {
                return false;
            }
        }
        return true;
    }

    private static class VMAT1XAdvice implements AdviceHook {

        public void startMethodGeneration() {
            byteArrayOut.addMethodStatus();
        }

        private static boolean generateTag(T1XTemplateTag tag, AdviceType at) {
            if (generateAll) {
                return true;
            }
            // otherwise we only generate if the tag (a) has the capability for the requested advice type
            // and (b) the program is being run to generate that kind of advice.
            return tagAdviceCapabilities[tag.ordinal()][at.ordinal()] && generating[at.ordinal()];
        }

        public void generate(T1XTemplateTag tag, AdviceType at, String... args) {
            byteArrayOut.setTag(tag);
            if (!generateTag(tag, at)) {
                // If both capabilities are present, then don't discard, even if we aren't generating the advice type
                if (!hasBeforeAndAfter(tag)) {
                    byteArrayOut.discard(tag);
                    //System.out.println("discarding: " + tag + ":" + at);
                }

                return;
            }
            adviceType = at;
            methodName = tag.opcode >= 0 ? codeMap.get(tag.opcode).methodName : "???";

            final String k = args.length > 0 ? args[0] : null;
            switch (tag) {
                case GETFIELD$byte:
                case GETFIELD$boolean:
                case GETFIELD$char:
                case GETFIELD$short:
                case GETFIELD$int:
                case GETFIELD$float:
                case GETFIELD$long:
                case GETFIELD$double:
                case GETFIELD$reference:
                case GETFIELD$word:
                    assert adviceType == AdviceType.BEFORE;
                    generateGetField(k, false);
                    break;

                case GETFIELD$byte$resolved:
                case GETFIELD$boolean$resolved:
                case GETFIELD$char$resolved:
                case GETFIELD$short$resolved:
                case GETFIELD$int$resolved:
                case GETFIELD$float$resolved:
                case GETFIELD$long$resolved:
                case GETFIELD$double$resolved:
                case GETFIELD$reference$resolved:
                case GETFIELD$word$resolved:
                    assert adviceType == AdviceType.BEFORE;
                    generateGetField(k, true);
                    break;

                case PUTFIELD$byte:
                case PUTFIELD$boolean:
                case PUTFIELD$char:
                case PUTFIELD$short:
                case PUTFIELD$int:
                case PUTFIELD$float:
                case PUTFIELD$long:
                case PUTFIELD$double:
                case PUTFIELD$reference:
                case PUTFIELD$word:
                    assert adviceType == AdviceType.BEFORE;
                    generatePutField(k, false);
                    break;

                case PUTFIELD$byte$resolved:
                case PUTFIELD$boolean$resolved:
                case PUTFIELD$char$resolved:
                case PUTFIELD$short$resolved:
                case PUTFIELD$int$resolved:
                case PUTFIELD$float$resolved:
                case PUTFIELD$long$resolved:
                case PUTFIELD$double$resolved:
                case PUTFIELD$reference$resolved:
                case PUTFIELD$word$resolved:
                    assert adviceType == AdviceType.BEFORE;
                    generatePutField(k, true);
                    break;

                case GETSTATIC$byte:
                case GETSTATIC$boolean:
                case GETSTATIC$char:
                case GETSTATIC$short:
                case GETSTATIC$int:
                case GETSTATIC$float:
                case GETSTATIC$long:
                case GETSTATIC$double:
                case GETSTATIC$reference:
                case GETSTATIC$word:
                    assert adviceType == AdviceType.BEFORE;
                    generateGetStatic(k, false);
                    break;

                case GETSTATIC$byte$init:
                case GETSTATIC$boolean$init:
                case GETSTATIC$char$init:
                case GETSTATIC$short$init:
                case GETSTATIC$int$init:
                case GETSTATIC$float$init:
                case GETSTATIC$long$init:
                case GETSTATIC$double$init:
                case GETSTATIC$reference$init:
                case GETSTATIC$word$init:
                    assert adviceType == AdviceType.BEFORE;
                    generateGetStatic(k, true);
                    break;

                case PUTSTATIC$byte:
                case PUTSTATIC$boolean:
                case PUTSTATIC$char:
                case PUTSTATIC$short:
                case PUTSTATIC$int:
                case PUTSTATIC$float:
                case PUTSTATIC$long:
                case PUTSTATIC$double:
                case PUTSTATIC$reference:
                case PUTSTATIC$word:
                    assert adviceType == AdviceType.BEFORE;
                    generatePutStatic(k, false);
                    break;

                case PUTSTATIC$byte$init:
                case PUTSTATIC$boolean$init:
                case PUTSTATIC$char$init:
                case PUTSTATIC$short$init:
                case PUTSTATIC$int$init:
                case PUTSTATIC$float$init:
                case PUTSTATIC$long$init:
                case PUTSTATIC$double$init:
                case PUTSTATIC$reference$init:
                case PUTSTATIC$word$init:
                    assert adviceType == AdviceType.BEFORE;
                    generatePutStatic(k, true);
                    break;

                case IALOAD:
                case LALOAD:
                case FALOAD:
                case DALOAD:
                case AALOAD:
                case BALOAD:
                case CALOAD:
                case SALOAD:
                    assert adviceType == AdviceType.BEFORE;
                    generateArrayLoad(k);
                    break;

                case IASTORE:
                case LASTORE:
                case FASTORE:
                case DASTORE:
                case AASTORE:
                case BASTORE:
                case CASTORE:
                case SASTORE:
                    assert adviceType == AdviceType.BEFORE;
                    generateArrayStore(k);
                    break;

                case ICONST_0:
                case ICONST_1:
                case ICONST_2:
                case ICONST_3:
                case ICONST_4:
                case ICONST_5:
                case ICONST_M1:
                case WCONST_0:
                case ACONST_NULL:
                    assert adviceType == AdviceType.BEFORE;
                    generateLoadConst(tag, k, args[1]);
                    break;

                case FCONST:
                case DCONST:
                case LCONST:
                    assert adviceType == AdviceType.BEFORE;
                    generateLoadConst(tag, k, null);
                    break;

                case ILOAD:
                case LLOAD:
                case FLOAD:
                case DLOAD:
                case ALOAD:
                case WLOAD:
                    assert adviceType == AdviceType.BEFORE;
                    generateLoad(k);
                    break;

                case ISTORE:
                case LSTORE:
                case FSTORE:
                case DSTORE:
                case ASTORE:
                case WSTORE:
                    assert adviceType == AdviceType.BEFORE;
                    generateStore(k);
                    break;

                case BIPUSH:
                case SIPUSH:
                    assert adviceType == AdviceType.BEFORE;
                    generateIPush(k);
                    break;

                case IADD:
                case LADD:
                case FADD:
                case DADD:
                case ISUB:
                case LSUB:
                case FSUB:
                case DSUB:
                case IMUL:
                case LMUL:
                case FMUL:
                case DMUL:
                case IDIV:
                case LDIV:
                case FDIV:
                case DDIV:
                case IREM:
                case LREM:
                case FREM:
                case DREM:
                case INEG:
                case LNEG:
                case FNEG:
                case DNEG:
                case ISHL:
                case LSHL:
                case ISHR:
                case LSHR:
                case IUSHR:
                case LUSHR:
                case IAND:
                case LAND:
                case IOR:
                case LOR:
                case IXOR:
                case LXOR:
                case LCMP:
                case FCMPL:
                case FCMPG:
                case DCMPL:
                case DCMPG:
                    assert adviceType == AdviceType.BEFORE;
                    generateOperation(tag, k);
                    break;

                case WDIV:
                case WDIVI:
                case WREM:
                case WREMI:
                    assert adviceType == AdviceType.BEFORE;
                    generateWOperation(tag);
                    break;

                case IINC:
                    assert adviceType == AdviceType.BEFORE;
                    generateIInc();
                    break;

                case I2L:
                case I2F:
                case I2D:
                case L2I:
                case L2F:
                case L2D:
                case F2I:
                case F2L:
                case F2D:
                case D2I:
                case D2L:
                case D2F:
                case I2B:
                case I2C:
                case I2S:
                case MOV_I2F:
                case MOV_F2I:
                case MOV_L2D:
                case MOV_D2L:
                    assert adviceType == AdviceType.BEFORE;
                    generateConversion(tag, k);
                    break;

                case IFEQ:
                case IFNE:
                case IFLT:
                case IFGE:
                case IFGT:
                case IFLE:
                case IFNULL:
                case IFNONNULL:
                case IF_ICMPEQ:
                case IF_ICMPNE:
                case IF_ICMPLT:
                case IF_ICMPGE:
                case IF_ICMPGT:
                case IF_ICMPLE:
                case IF_ACMPEQ:
                case IF_ACMPNE:
                    assert adviceType == AdviceType.BEFORE;
                    generateIf(tag);
                    break;

                case CHECKCAST:
                case CHECKCAST$resolved:
                case INSTANCEOF:
                case INSTANCEOF$resolved:
                    assert adviceType == AdviceType.BEFORE;
                    generateTypeCheck(tag);
                    break;


                case NEW:
                case NEW$init:
                    assert adviceType == AdviceType.AFTER;
                    generateNew();
                    break;

                case NEWARRAY:
                case ANEWARRAY:
                case ANEWARRAY$resolved:
                    assert adviceType == AdviceType.AFTER;
                    generateNewUniArray();
                    break;

                case MULTIANEWARRAY:
                case MULTIANEWARRAY$resolved:
                    assert adviceType == AdviceType.AFTER;
                    generateNewMultiArray();
                    break;

                case INVOKEVIRTUAL$void:
                case INVOKEVIRTUAL$float:
                case INVOKEVIRTUAL$long:
                case INVOKEVIRTUAL$double:
                case INVOKEVIRTUAL$word:
                case INVOKEVIRTUAL$reference:
                    generateInvokeVirtual(false);
                    break;
                case INVOKEVIRTUAL$void$resolved:
                case INVOKEVIRTUAL$float$resolved:
                case INVOKEVIRTUAL$long$resolved:
                case INVOKEVIRTUAL$double$resolved:
                case INVOKEVIRTUAL$word$resolved:
                case INVOKEVIRTUAL$reference$resolved:
                case INVOKEVIRTUAL$void$instrumented:
                case INVOKEVIRTUAL$float$instrumented:
                case INVOKEVIRTUAL$long$instrumented:
                case INVOKEVIRTUAL$double$instrumented:
                case INVOKEVIRTUAL$word$instrumented:
                case INVOKEVIRTUAL$reference$instrumented:
                    generateInvokeVirtual(true);
                    break;

                case INVOKESPECIAL$void:
                case INVOKESPECIAL$float:
                case INVOKESPECIAL$long:
                case INVOKESPECIAL$double:
                case INVOKESPECIAL$word:
                case INVOKESPECIAL$reference:
                    generateInvokeSpecial(false);
                    break;
                case INVOKESPECIAL$void$resolved:
                case INVOKESPECIAL$float$resolved:
                case INVOKESPECIAL$long$resolved:
                case INVOKESPECIAL$double$resolved:
                case INVOKESPECIAL$word$resolved:
                case INVOKESPECIAL$reference$resolved:
                    generateInvokeSpecial(true);
                    break;

                case INVOKESTATIC$void:
                case INVOKESTATIC$float:
                case INVOKESTATIC$long:
                case INVOKESTATIC$double:
                case INVOKESTATIC$word:
                case INVOKESTATIC$reference:
                    generateInvokeStatic(false);
                    break;
                case INVOKESTATIC$void$init:
                case INVOKESTATIC$float$init:
                case INVOKESTATIC$long$init:
                case INVOKESTATIC$double$init:
                case INVOKESTATIC$word$init:
                case INVOKESTATIC$reference$init:
                    generateInvokeStatic(true);
                    break;

                case INVOKEINTERFACE$void:
                case INVOKEINTERFACE$float:
                case INVOKEINTERFACE$long:
                case INVOKEINTERFACE$double:
                case INVOKEINTERFACE$word:
                case INVOKEINTERFACE$reference:
                    generateInvokeInterface(false);
                    break;

                case INVOKEINTERFACE$void$resolved:
                case INVOKEINTERFACE$float$resolved:
                case INVOKEINTERFACE$long$resolved:
                case INVOKEINTERFACE$double$resolved:
                case INVOKEINTERFACE$word$resolved:
                case INVOKEINTERFACE$reference$resolved:
                case INVOKEINTERFACE$void$instrumented:
                case INVOKEINTERFACE$float$instrumented:
                case INVOKEINTERFACE$long$instrumented:
                case INVOKEINTERFACE$double$instrumented:
                case INVOKEINTERFACE$word$instrumented:
                case INVOKEINTERFACE$reference$instrumented:
                    generateInvokeInterface(true);
                    break;

                case ATHROW:
                    generateThrow();
                    break;

                case ARRAYLENGTH:
                    generateArrayLength();
                    break;

                case MONITORENTER:
                case MONITOREXIT:
                    generateMonitor(tag);
                    break;

                case POP:
                case POP2:
                case DUP:
                case DUP_X1:
                case DUP_X2:
                case DUP2:
                case DUP2_X1:
                case DUP2_X2:
                case SWAP:
                    generateStackAdjust(tag);
                    break;


                case LDC$int:
                case LDC$long:
                case LDC$float:
                case LDC$double:
                case LDC$reference:
                case LDC$reference$resolved:
                    generateLDC(tag, k);
                    break;

                case IRETURN:
                case LRETURN:
                case FRETURN:
                case DRETURN:
                case ARETURN:
                case WRETURN:
                case RETURN:

                case IRETURN$unlockClass:
                case IRETURN$unlockReceiver:
                case LRETURN$unlockClass:
                case LRETURN$unlockReceiver:
                case FRETURN$unlockClass:
                case FRETURN$unlockReceiver:
                case DRETURN$unlockClass:
                case DRETURN$unlockReceiver:
                case ARETURN$unlockClass:
                case ARETURN$unlockReceiver:
                case WRETURN$unlockClass:
                case WRETURN$unlockReceiver:
                case RETURN$unlockClass:
                case RETURN$unlockReceiver:
                    generateReturn(tag, k);
                    break;

                case LOCK_RECEIVER:
                case LOCK_CLASS:
                    methodName = "MonitorEnter";
                    generateMonitor(tag);
                    break;

                case UNLOCK_CLASS:
                case UNLOCK_RECEIVER:
                    methodName = "MonitorExit";
                    generateMonitor(tag);
                    break;

                // No special treatment for the following codes
                case RETURN$registerFinalizer:

                case PREAD_BYTE:
                case PREAD_CHAR:
                case PREAD_SHORT:
                case PREAD_INT:
                case PREAD_FLOAT:
                case PREAD_LONG:
                case PREAD_DOUBLE:
                case PREAD_WORD:
                case PREAD_REFERENCE:

                case PREAD_BYTE_I:
                case PREAD_CHAR_I:
                case PREAD_SHORT_I:
                case PREAD_INT_I:
                case PREAD_FLOAT_I:
                case PREAD_LONG_I:
                case PREAD_DOUBLE_I:
                case PREAD_WORD_I:
                case PREAD_REFERENCE_I:

                case PWRITE_BYTE:
                case PWRITE_SHORT:
                case PWRITE_INT:
                case PWRITE_FLOAT:
                case PWRITE_LONG:
                case PWRITE_DOUBLE:
                case PWRITE_WORD:
                case PWRITE_REFERENCE:

                case PWRITE_BYTE_I:
                case PWRITE_SHORT_I:
                case PWRITE_INT_I:
                case PWRITE_FLOAT_I:
                case PWRITE_LONG_I:
                case PWRITE_DOUBLE_I:
                case PWRITE_WORD_I:
                case PWRITE_REFERENCE_I:

                case PGET_BYTE:
                case PGET_CHAR:
                case PGET_SHORT:
                case PGET_INT:
                case PGET_FLOAT:
                case PGET_LONG:
                case PGET_DOUBLE:
                case PGET_WORD:
                case PGET_REFERENCE:

                case PSET_BYTE:
                case PSET_SHORT:
                case PSET_INT:
                case PSET_FLOAT:
                case PSET_LONG:
                case PSET_DOUBLE:
                case PSET_WORD:
                case PSET_REFERENCE:

                case PCMPSWP_INT:
                case PCMPSWP_WORD:
                case PCMPSWP_REFERENCE:

                case PCMPSWP_INT_I:
                case PCMPSWP_WORD_I:
                case PCMPSWP_REFERENCE_I:
                    generateDefault(tag);
                    break;

                case TRACE_METHOD_ENTRY:
                    generateTraceMethodEntry();
                    break;

                default:
                    ProgramError.unexpected("tag " + tag + " not implemented");
            }
        }
    }

    private static void startGuardAdvice() {
        out.printf("        if (%s) {%n", bitGuard ? ALT_ISADVISING : INLINE_ISADVISING);
    }

    private static void endGuardAdvice() {
        out.printf("        }%n");
    }

    private static void generatePutField(String k, boolean resolved) {
        String offset = resolved ? "offset" : "f.offset()";
        startGuardAdvice();
        out.printf(METHOD_PREFIX + "(object, %s, %s);%n", adviceType.methodNameComponent, methodName, offset, putValue(k, ""));
        endGuardAdvice();
    }

    private static void generateGetField(String k, boolean resolved) {
        String offset = resolved ? "offset" : "f.offset()";
        startGuardAdvice();
        out.printf(METHOD_PREFIX + "(object, %s);%n", adviceType.methodNameComponent, methodName, offset);
        endGuardAdvice();
    }

    private static void generatePutStatic(String k, boolean init) {
        String args = init ? "staticTuple, offset" : "f.holder().staticTuple(), f.offset()";
        startGuardAdvice();
        out.printf(METHOD_PREFIX + "(%s, %s);%n", adviceType.methodNameComponent, methodName, args, putValue(k, ""));
        endGuardAdvice();
    }

    private static void generateGetStatic(String k, boolean init) {
        String args = init ? "staticTuple, offset" : "f.holder().staticTuple(), f.offset()";
        startGuardAdvice();
        out.printf(METHOD_PREFIX + "(%s);%n", adviceType.methodNameComponent, methodName, args);
        endGuardAdvice();
    }

    private static void generateArrayLoad(String k) {
        startGuardAdvice();
        out.printf(METHOD_PREFIX + "(array, index);%n", adviceType.methodNameComponent, methodName);
        endGuardAdvice();
    }

    private static void generateArrayStore(String k) {
        startGuardAdvice();
        out.printf(METHOD_PREFIX + "(array, index, %s);%n", adviceType.methodNameComponent, methodName, putValue(k, ""));
        endGuardAdvice();
    }

    private static void generateLoadConst(T1XTemplateTag tag, String k, String v) {
        String value = k.equals("int") || k.equals("Reference") ? v : (k.equals("Word") ? "0" : "constant");
        startGuardAdvice();
        out.printf(METHOD_PREFIX + "(%s);%n", adviceType.methodNameComponent, methodName, value);
        endGuardAdvice();
    }

    private static void generateLDC(T1XTemplateTag tag, String k) {
        startGuardAdvice();
        out.printf(METHOD_PREFIX + "(constant);%n", adviceType.methodNameComponent, methodName);
        endGuardAdvice();
    }

    private static void generateLoad(String k) {
        startGuardAdvice();
        out.printf(METHOD_PREFIX + "(dispToLocalSlot);%n", adviceType.methodNameComponent, methodName);
        endGuardAdvice();
    }

    private static void generateStore(String k) {
        startGuardAdvice();
        out.printf(METHOD_PREFIX + "(dispToLocalSlot, %s);%n", adviceType.methodNameComponent, methodName, putValue(k));
        endGuardAdvice();
    }

    private static void generateIPush(String k) {
        startGuardAdvice();
        out.printf(METHOD_PREFIX + "(value);%n", adviceType.methodNameComponent, methodName);
        endGuardAdvice();
    }

    private static void generateOperation(T1XTemplateTag tag, String k) {
        String value1 = NEG_TEMPLATE_TAGS.contains(tag) ? "value" : "value1";
        String value2 = NEG_TEMPLATE_TAGS.contains(tag) ? getDefault(k) : "value2";
        startGuardAdvice();
        out.printf(METHOD_PREFIX + "(%d, %s, %s);%n", adviceType.methodNameComponent, methodName, tag.opcode, value1, value2);
        endGuardAdvice();
    }

    private static void generateWOperation(T1XTemplateTag tag) {
        String value1 = "value1.toLong()";
        String value2 = tag == T1XTemplateTag.WDIVI || tag == T1XTemplateTag.WREMI ? "value2" : "value2.toLong()";
        startGuardAdvice();
        out.printf(METHOD_PREFIX + "(%d, %s, %s);%n", adviceType.methodNameComponent, methodName, tag.opcode, value1, value2);
        endGuardAdvice();
    }

    private static void generateConversion(T1XTemplateTag tag, String k) {
        startGuardAdvice();
        out.printf(METHOD_PREFIX + "(%d, value);%n", adviceType.methodNameComponent, methodName, tag.opcode);
        endGuardAdvice();
    }

    private static void generateNew() {
        startGuardAdvice();
        out.printf(METHOD_PREFIX + "(object);%n", adviceType.methodNameComponent, methodName);
        endGuardAdvice();
    }

    private static void generateNewUniArray() {
        startGuardAdvice();
        out.printf(METHOD_PREFIX + "(array, length);%n", adviceType.methodNameComponent, methodName);
        endGuardAdvice();
    }

    private static void generateNewMultiArray() {
        startGuardAdvice();
        out.printf(METHOD_PREFIX + "(array, lengths);%n", adviceType.methodNameComponent, methodName);
        endGuardAdvice();
    }

    private static void generateInvokeVirtual(boolean resolved) {
        startGuardAdvice();
        out.printf(METHOD_PREFIX + "(receiver, methodActor);%n", adviceType.methodNameComponent, methodName);
        endGuardAdvice();
    }

    private static void generateInvokeInterface(boolean resolved) {
        startGuardAdvice();
        out.printf(METHOD_PREFIX + "(receiver, interfaceMethodActor);%n", adviceType.methodNameComponent, methodName);
        endGuardAdvice();
    }

    private static void generateInvokeSpecial(boolean resolved) {
        startGuardAdvice();
        out.printf(METHOD_PREFIX + "(receiver, methodActor);%n", adviceType.methodNameComponent, methodName);
        endGuardAdvice();
    }

    private static void generateInvokeStatic(boolean resolved) {
        startGuardAdvice();
        out.printf(METHOD_PREFIX + "(null, methodActor);%n", adviceType.methodNameComponent, methodName);
        endGuardAdvice();
    }

    private static void generateTraceMethodEntry() {
        startGuardAdvice();
        out.printf(METHOD_PREFIX + "(receiver, methodActor);%n", adviceType.methodNameComponent, "MethodEntry");
        endGuardAdvice();

    }

    private static void generateIf(T1XTemplateTag tag) {
        boolean isNull = tag == T1XTemplateTag.IFNULL || tag == T1XTemplateTag.IFNONNULL;
        String value1 = isNull || IF_TEMPLATE_TAGS.contains(tag) ? "value" : "value1";
        String value2 = isNull ? "null" : (IF_TEMPLATE_TAGS.contains(tag) ? "0" : "value2");
        startGuardAdvice();
        out.printf(METHOD_PREFIX + "(%d, %s, %s);%n", adviceType.methodNameComponent, methodName, tag.opcode, value1, value2);
        endGuardAdvice();
    }

    private static void generateTypeCheck(T1XTemplateTag tag) {
        String name = CHECKCAST_TEMPLATE_TAGS.contains(tag) ? "CheckCast" : "InstanceOf";
        startGuardAdvice();
        out.printf(METHOD_PREFIX + "(object, classActor);%n", adviceType.methodNameComponent, methodName, name);
        endGuardAdvice();
    }

    private static void generateThrow() {
        startGuardAdvice();
        out.printf(METHOD_PREFIX + "(object);%n", adviceType.methodNameComponent, methodName);
        endGuardAdvice();
    }

    private static void generateArrayLength() {
        startGuardAdvice();
        out.printf(METHOD_PREFIX + "(array, length);%n", adviceType.methodNameComponent, methodName);
        endGuardAdvice();
    }

    private static void generateMonitor(T1XTemplateTag tag) {
        startGuardAdvice();
        out.printf(METHOD_PREFIX + "(object);%n", adviceType.methodNameComponent, methodName);
        endGuardAdvice();
    }

    private static void generateReturn(T1XTemplateTag tag, String k) {
        String arg = codeMap.get(tag.opcode) == VMABytecodes.RETURN ? "" : putArg("result", k, "");
        startGuardAdvice();
        out.printf(METHOD_PREFIX + "(%s);%n", adviceType.methodNameComponent, methodName, arg);
        endGuardAdvice();
    }

    private static void generateIInc() {
        startGuardAdvice();
        out.printf(METHOD_PREFIX + "(dispToLocalSlot, value, increment);%n", adviceType.methodNameComponent, methodName);
        endGuardAdvice();
    }

    private static void generateStackAdjust(T1XTemplateTag tag) {
        startGuardAdvice();
        out.printf(METHOD_PREFIX + "(%d);%n", adviceType.methodNameComponent, methodName, tag.opcode);
        endGuardAdvice();
    }

    private static void generateDefault(T1XTemplateTag tag) {
        startGuardAdvice();
        out.printf(METHOD_PREFIX + "(%d);%n", adviceType.methodNameComponent, methodName, tag.opcode);
        endGuardAdvice();
    }


    private static String putValue(String k) {
        return putValue(k, "");
    }
    private static String putValue(String k, String vSuffix) {
        return putArg("value", k, vSuffix);
    }

    private static String putArg(String name, String k, String vSuffix) {
        String value = name + vSuffix;
        if (k.equals("Word")) {
            return value + ".asAddress().toLong()";
        } else if (k.equals("boolean")) {
            return value + " ? 1 : 0";
        } else {
            return value;
        }
    }

    private static String getDefault(String k) {
        if (k.equals("float")) {
            return "0.0f";
        } else if (k.equals("double")) {
            return "0.0";
        } else if (k.equals("Object")) {
            return "null";
        } else {
            return "0";
        }
    }

    VMAdviceTemplateGenerator() {
        super(VMAdviceTemplateGenerator.class);
    }

    public static void main(String[] args) {
        generateAll = true;
        for (String arg : args) {
            if (arg.equals("before")) {
                generating[AdviceType.BEFORE.ordinal()] = true;
                generateAll = false;
            } else if (arg.equals("after")) {
                generating[AdviceType.AFTER.ordinal()] = true;
                generateAll = false;
            } else if (arg.equals("javaguard")) {
                bitGuard = false;
            }
        }
        byteArrayOut = new ThisByteArrayOutputStream();
        out = new PrintStream(byteArrayOut);

        t1xTemplateGen = new VMAdviceTemplateGenerator();
        // discover what the advice capabilities are for each tag
        t1xTemplateGen.generateAll(new DiscoverCapabilitiesHook());

        byteArrayOut.reset();
        t1xTemplateGen.generateAll(new VMAT1XAdvice());
        byteArrayOut.writeOut();
    }

    /**
     * Generate a specific {@code INVOKE} template.
     * @param k type
     * @param variant one of "virtual" or "interface"
     * @param tag one of "", "resolved" or "instrumented"
     */
    @Override
    public void generateInvokeVITemplate(String k, String variant, String tag) {
        boolean isVoid = k.equals("void");
        String param1 = tag.equals("") ? "ResolutionGuard.InPool guard" :
            (variant.equals("interface") ? "InterfaceMethodActor interfaceMethodActor" : "VirtualMethodActor methodActor");
        param1 += ", int receiverStackIndex";
        if (tag.equals("instrumented")) {
            param1 += ", MethodProfile mpo, int mpoIndex";
        }
        t1xTemplateGen.generateAutoComment();
        t1xTemplateGen.generateTemplateTag("INVOKE%s$%s%s", variant.toUpperCase(), lType(k), prefixDollar(tag));
        out.printf("    public static void invoke%s%s(%s) {%n", variant, uType(k), param1);
        out.printf("        Object receiver = peekObject(receiverStackIndex);%n");
        if (variant.equals("interface")) {
            if (tag.equals("")) {
                out.printf("        final InterfaceMethodActor interfaceMethodActor = Snippets.resolveInterfaceMethod(guard);%n");
                out.printf("        Address entryPoint = VMAT1XRuntime.selectInterfaceMethod(receiver, interfaceMethodActor);%n");
            } else if (tag.equals("resolved")) {
                out.printf("        Address entryPoint = VMAT1XRuntime.selectInterfaceMethod(receiver, interfaceMethodActor);%n");
            } else if (tag.equals("instrumented")) {
                out.printf("        Address entryPoint = Snippets.selectInterfaceMethod(receiver, interfaceMethodActor, mpo, mpoIndex);%n");
            }
        } else {
            // virtual
            if (tag.equals("")) {
                out.printf("        VirtualMethodActor methodActor = Snippets.resolveVirtualMethod(guard);%n");
                out.printf("        Address entryPoint = VMAT1XRuntime.selectNonPrivateVirtualMethod(receiver, methodActor);%n");
            } else if (tag.equals("resolved")) {
                out.printf("        Address entryPoint = ObjectAccess.readHub(receiver).getWord(methodActor.vTableIndex()).asAddress();%n");
            } else if (tag.equals("instrumented")) {
                out.printf("        Address entryPoint = selectVirtualMethod(receiver, methodActor.vTableIndex(), mpo, mpoIndex);%n");
            }
        }
        generateBeforeAdvice(k, variant, tag);
        out.printf("        ");
        if (!isVoid) {
            out.printf("final %s result = ", oType(k));
        }
        out.printf("indirectCall%s(entryPoint, CallEntryPoint.VTABLE_ENTRY_POINT, receiver);%n", uoType(k));
        generateAfterAdvice(k, variant, tag);
        if (!isVoid) {
            out.printf("        push%s(result);%n", uoType(k));
        }
        out.printf("    }%n");
        newLine();
    }


    /**
     * Generate a specific {@code INVOKE} template.
     * @param k type
     * @param variant one of "special" or "static"
     * @param xtag one of "" or "init" or "resolved"
     */
    @Override
    public void generateInvokeSSTemplate(String k, String variant, String xtag) {
        boolean resolved = xtag.equals("resolved");
        boolean isStatic = variant.equals("static");
        String tag = isStatic && resolved ? "init" : xtag;
        boolean isVoid = k.equals("void");
        String params = xtag.equals("") ? "ResolutionGuard.InPool guard" :
            (isStatic ? "StaticMethodActor methodActor" : "VirtualMethodActor methodActor");
        if (variant.equals("special")) {
            if (params.length() > 0) {
                params += ", ";
            }
            params += "int receiverStackIndex";
        }
        t1xTemplateGen.generateAutoComment();
        t1xTemplateGen.generateTemplateTag("INVOKE%s$%s%s", variant.toUpperCase(), lType(k), prefixDollar(tag));
        out.printf("    public static void invoke%s%s(%s) {%n", variant, uType(k), params);
        if (!isStatic) {
            out.printf("        Object receiver = peekObject(receiverStackIndex);%n");
            out.printf("        nullCheck(Reference.fromJava(receiver).toOrigin());%n");
        }
        if (!resolved) {
            out.printf("        %sMethodActor methodActor = VMAT1XRuntime.resolve%sMethod(guard);%n", isStatic ? "Static" : "Virtual", toFirstUpper(variant));
        }
        generateBeforeAdvice(k, variant, tag);
        out.printf("        ");
        if (!isVoid) {
            out.printf("final %s result = ", oType(k));
        }
        if (resolved) {
            out.printf("directCall%s();%n", uoType(k));
        } else {
            out.printf("indirectCall%s(VMAT1XRuntime.initialize%sMethod(methodActor), CallEntryPoint.OPTIMIZED_ENTRY_POINT);%n", uoType(k), toFirstUpper(variant));
        }
        generateAfterAdvice(k, variant, tag);
        if (!isVoid) {
            out.printf("        push%s(result);%n", uoType(k));
        }
        out.printf("    }%n");
        newLine();
    }


    @Override
    public void generateTraceMethodEntryTemplate() {
        generateAutoComment();
        generateTemplateTag("%s", TRACE_METHOD_ENTRY);
        out.printf("    public static void traceMethodEntry(MethodActor methodActor, int dispToLocalSlot) {%n");
        out.printf("        Object receiver = dispToLocalSlot == 0 ? null : getLocalObject(dispToLocalSlot);%n");
        generateAfterAdvice();
        out.printf("    }%n");
        newLine();

    }

}