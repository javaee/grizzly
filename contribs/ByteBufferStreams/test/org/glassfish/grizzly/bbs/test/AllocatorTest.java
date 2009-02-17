/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2003-2007 Sun Microsystems, Inc. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 *
 * Contributor(s):
 *
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package org.glassfish.grizzly.bbs.test  ;

import org.glassfish.grizzly.bbs.*;
import java.nio.ByteBuffer ;

import org.testng.annotations.Test ;
import org.testng.annotations.BeforeGroups ;
import org.testng.annotations.AfterGroups ;
import org.testng.Assert ;

import org.glassfish.grizzly.bbs.impl.Slab ;
import org.glassfish.grizzly.bbs.Allocator ;

public class AllocatorTest {
    private static final int SLAB_SIZE = 100000 ;

    private Slab testSlab ;

    // Test Slab
    @BeforeGroups( groups = { "Slab" } )
    public void initSlab() {
        testSlab = new Slab( SLAB_SIZE, Allocator.BufferType.HEAP ) ;
        Assert.assertTrue( testSlab.getState() == Slab.State.EMPTY ) ;
    }

    @Test( groups = { "Slab" } )
    public void testTotalSize() {
        Assert.assertEquals( testSlab.totalSize(), SLAB_SIZE ) ;
    }

    private static final int[] ALLOCATION_SIZES = new int[] {
        12, 200, 34, 5732, 45, 12340, 1200, 2000, 2500, 456, 5200 
    } ;

    private void testSlabState( Slab testSlab, int available, int allocated, 
        int disposed, Slab.State state ) {

        Assert.assertEquals( testSlab.sizeAvailable(), available ) ;
        Assert.assertEquals( testSlab.sizeAllocated(), allocated ) ;
        Assert.assertEquals( testSlab.sizeDisposed(), disposed ) ;
        Assert.assertEquals( testSlab.getState(), state ) ;
    }

    @Test( groups = { "Slab" } )
    // Test allocate and dispose, along with state changes and sizeXXX methods.
    public void testAllocate() {
        testSlab.markEmpty() ;

        testSlabState( testSlab, SLAB_SIZE, 0, 
            0, Slab.State.EMPTY ) ;

        int totalAllocations = 0 ;
        ByteBuffer[] allocations = new ByteBuffer[ALLOCATION_SIZES.length] ;
        for (int ctr =0; ctr < ALLOCATION_SIZES.length; ctr++) {
            int size = ALLOCATION_SIZES[ctr] ;
            allocations[ctr] = testSlab.allocate( size ) ;
            totalAllocations += size ;
        }

        Assert.assertNull( testSlab.allocate( SLAB_SIZE ) ) ;

        testSlabState( testSlab, SLAB_SIZE - totalAllocations, 
            totalAllocations, 0, Slab.State.PARTIAL ) ;

        for (ByteBuffer buffer : allocations) {
            testSlab.dispose( buffer ) ;
        }

        testSlabState( testSlab, SLAB_SIZE - totalAllocations, 
            totalAllocations, totalAllocations, Slab.State.PARTIAL ) ;

        // This call to markFull should actually cause the buffer to become 
        // empty!
        testSlab.markFull() ;

        testSlabState( testSlab, SLAB_SIZE, 0,    
            0, Slab.State.EMPTY ) ;

        testSlab.markEmpty() ;

        testSlabState( testSlab, SLAB_SIZE, 0,    
            0, Slab.State.EMPTY ) ;
    }

    @Test( groups = { "Slab" } )
    public void testTrim() {
        testSlab.markEmpty() ;

        int testSize = 5400 ;

        testSlabState( testSlab, SLAB_SIZE, 0, 
            0, Slab.State.EMPTY ) ;

        // just throw some space away
        testSlab.allocate( testSize ) ;

        int startPos = testSlab.currentPosition() ;
        ByteBuffer buff = testSlab.allocate( testSize ) ;

        testSlabState( testSlab, SLAB_SIZE - 2*testSize, 2*testSize, 
            0, Slab.State.PARTIAL ) ;

        Assert.assertEquals( buff.capacity(), testSize ) ;
        Assert.assertEquals( buff.limit(), buff.capacity() ) ;
        Assert.assertEquals( buff.position(), 0 ) ;

        ByteBuffer newBuffer = testSlab.trim( startPos, buff, testSize/2 ) ;
        Assert.assertNotSame( buff, newBuffer ) ;

        int expectedSpaceAllocated = 2*testSize - testSize/2 ;

        testSlabState( testSlab, SLAB_SIZE - expectedSpaceAllocated, 
            expectedSpaceAllocated, 0, Slab.State.PARTIAL ) ;
    }

    @AfterGroups( groups = { "Slab" } ) 
    public void finishSlab() {
        testSlab = null ;
    }

    // Test SlabPool
    // Test Allocator (pool impl)
}

