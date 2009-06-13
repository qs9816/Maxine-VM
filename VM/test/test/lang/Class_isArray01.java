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
/*
 * @Harness: java
 * @Runs: 0 = false; 1 = true; 2 = false; 3 = true; 4 = false; 5 = false; 6 = false; 7 = false; 8 = false
 */
package test.lang;


public final class Class_isArray01 {
    private Class_isArray01() {
    }

    public static boolean test(int i) {
        if (i == 0) {
            return int.class.isArray();
        }
        if (i == 1) {
            return int[].class.isArray();
        }
        if (i == 2) {
            return Object.class.isArray();
        }
        if (i == 3) {
            return Object[].class.isArray();
        }
        if (i == 4) {
            return Class_isArray01.class.isArray();
        }
        if (i == 5) {
            return Cloneable.class.isArray();
        }
        if (i == 6) {
            return Runnable.class.isArray();
        }
        if (i == 7) {
            return void.class.isArray();
        }
        return false;
    }
}
