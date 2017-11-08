/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2017 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://oss.oracle.com/licenses/CDDL+GPL-1.1
 * or LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
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

package org.glassfish.grizzly.http2;

import org.junit.Test;

import static junit.framework.Assert.assertNotNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class NaryTreeTest {


    // ----------------------------------------------------------- Test Methods


    @Test
    public void addSibling() {
        final Node root = new TestNode(0);
        root.addChild(new TestNode(1));
        root.firstChild.addSibling(new TestNode(2));
        root.firstChild.addSibling(new TestNode(3));
        assertEquals(3, root.firstChild.id);
        assertEquals(2, root.firstChild.next.id);
        assertEquals(3, root.firstChild.next.prev.id);
        assertEquals(1, root.firstChild.next.next.id);
        assertEquals(2, root.firstChild.next.next.prev.id);
    }

    @Test
    public void addChild() {
        final Node root = new TestNode(0);
        root.addChild(new TestNode(1));
        root.addChild(new TestNode(2));
        root.addChild(new TestNode(3));
        assertEquals(3, root.firstChild.id);
        assertEquals(2, root.firstChild.next.id);
        assertEquals(3, root.firstChild.next.prev.id);
        assertEquals(1, root.firstChild.next.next.id);
        assertEquals(2, root.firstChild.next.next.prev.id);
    }

    @Test
    public void addExclusiveChild1() {
        final Node root = new TestNode(0);
        root.addChild(new TestNode(1));
        root.addChild(new TestNode(2));
        root.addChild(new TestNode(3), true);
        assertEquals(3, root.firstChild.id);
        assertTrue(root.firstChild.exclusive);
        assertEquals(0, root.firstChild.parent.id);
        assertNull(root.firstChild.next);
        assertNull(root.firstChild.prev);
        assertEquals(2, root.firstChild.firstChild.id);
        assertEquals(1, root.firstChild.firstChild.next.id);
        assertEquals(3, root.firstChild.firstChild.parent.id);
    }

    @Test
    public void addExclusiveChild2() {
        final Node root = new TestNode(0);
        root.addChild(new TestNode(1));
        root.addChild(new TestNode(2));
        root.addChild(new TestNode(3), true);
        root.addChild(new TestNode(4), true);
        assertEquals(4, root.firstChild.id);
        assertTrue(root.firstChild.exclusive);
        assertEquals(0, root.firstChild.parent.id);
        assertEquals(3, root.firstChild.firstChild.id);
        assertTrue(root.firstChild.firstChild.exclusive);
        assertEquals(4, root.firstChild.firstChild.parent.id);
        assertNull(root.firstChild.next);
        assertNull(root.firstChild.prev);
        assertEquals(2, root.firstChild.firstChild.firstChild.id);
        assertEquals(1, root.firstChild.firstChild.firstChild.next.id);
        assertEquals(3, root.firstChild.firstChild.firstChild.parent.id);
    }

    @Test
    public void addExclusiveChild3() {
        final Node root = new TestNode(0);
        final Node childToAdd = new TestNode(1);
        childToAdd.addChild(new TestNode(2));
        root.addChild(childToAdd, true);
        assertEquals(1, root.firstChild.id);
        assertTrue(root.firstChild.exclusive);
        assertEquals(0, root.firstChild.parent.id);
        assertEquals(2, root.firstChild.firstChild.id);
        assertEquals(1, root.firstChild.firstChild.parent.id);
        assertNull(root.firstChild.next);
        assertNull(root.firstChild.prev);
    }

    @Test
    public void addExclusiveChild4() {
        final Node root = new TestNode(0);
        root.addChild(new TestNode(1));
        final Node childToAdd = new TestNode(2);
        root.addChild(childToAdd, true);
        assertEquals(2, root.firstChild.id);
        assertTrue(root.firstChild.exclusive);
        assertEquals(0, root.firstChild.parent.id);
        assertEquals(1, root.firstChild.firstChild.id);
        assertEquals(2, root.firstChild.firstChild.parent.id);
        assertNull(root.firstChild.next);
        assertNull(root.firstChild.prev);
    }

    @Test
    public void addExclusiveChild5() {
        final Node root = new TestNode(0);
        root.addChild(new TestNode(1));
        root.addChild(new TestNode(2));
        final Node childToAdd = new TestNode(3);
        childToAdd.addChild(new TestNode(4));
        childToAdd.addChild(new TestNode(5));
        root.addChild(childToAdd, true);

        assertEquals(0, root.id);
        assertEquals(3, root.firstChild.id);
        assertEquals(2, root.firstChild.firstChild.id);
        assertEquals(1, root.firstChild.firstChild.next.id);
        assertEquals(5, root.firstChild.firstChild.next.next.id);
        assertEquals(4, root.firstChild.firstChild.next.next.next.id);

        assertTrue(root.firstChild.exclusive);
        assertEquals(0, root.firstChild.parent.id);
        assertEquals(3, root.firstChild.firstChild.parent.id);
        assertEquals(3, root.firstChild.firstChild.next.next.parent.id);
        assertNull(root.firstChild.next);
        assertNull(root.firstChild.prev);
    }

    @Test
    public void find() {
        createAndValidate(); // No Exception thrown, find works.
    }

    @Test
    public void remove() {
        final Node root = createAndValidate();

        final Node removed = root.remove(4);
        assertNotNull(removed);
        assertNull(removed.parent);
        assertNull(removed.prev);
        assertNull(removed.next);
        assertNull(removed.firstChild);
        assertNull(root.remove(4));

        // validate the structure of the tree after the removal of the node.
        Node n1 = root.find(1);
        assertEquals(1, n1.id);
        assertEquals(0, n1.parent.id);
        assertEquals(2, n1.prev.id);
        assertEquals(10, n1.firstChild.id);
        assertNull(n1.next);

        Node n2 = root.find(2);
        assertEquals(2, n2.id);
        assertEquals(0, n2.parent.id);
        assertEquals(1, n2.next.id);
        assertEquals(6, n2.firstChild.id);
        assertNull(n2.prev);

        Node n3 = root.find(3);
        assertEquals(3, n3.id);
        assertEquals(1, n3.parent.id);
        assertEquals(9, n3.prev.id);
        assertEquals(8, n3.firstChild.id);
        assertNull(n3.next);

        Node n5 = root.find(5);
        assertEquals(5, n5.id);
        assertEquals(2, n5.parent.id);
        assertEquals(6, n5.prev.id);
        assertNull(n5.firstChild);
        assertNull(n5.next);

        Node n6 = root.find(6);
        assertEquals(6, n6.id);
        assertEquals(2, n6.parent.id);
        assertEquals(5, n6.next.id);
        assertNull(n6.firstChild);
        assertNull(n6.prev);

        Node n7 = root.find(7);
        assertEquals(7, n7.id);
        assertEquals(3, n7.parent.id);
        assertEquals(8, n7.prev.id);
        assertNull(n7.firstChild);
        assertNull(n7.next);

        Node n8 = root.find(8);
        assertEquals(8, n8.id);
        assertEquals(3, n8.parent.id);
        assertEquals(7, n8.next.id);
        assertNull(n8.firstChild);
        assertNull(n8.prev);

        Node n9 = root.find(9);
        assertEquals(9, n9.id);
        assertEquals(1, n9.parent.id);
        assertEquals(10, n9.prev.id);
        assertEquals(3, n9.next.id);
        assertEquals(12, n9.firstChild.id);

        Node n10 = root.find(10);
        assertEquals(10, n10.id);
        assertEquals(1, n10.parent.id);
        assertEquals(9, n10.next.id);
        assertNull(n10.firstChild);
        assertNull(n10.prev);

        Node n11 = root.find(11);
        assertEquals(11, n11.id);
        assertEquals(9, n11.parent.id);
        assertEquals(12, n11.prev.id);
        assertNull(n11.firstChild);
        assertNull(n11.next);

        Node n12 = root.find(12);
        assertEquals(12, n12.id);
        assertEquals(9, n12.parent.id);
        assertEquals(11, n12.next.id);
        assertNull(n12.firstChild);
        assertNull(n12.prev);
    }

    @Test
    public void detach() {
        final Node root = createAndValidate();

        final Node removed = root.detach(4);
        assertNotNull(removed);
        assertNull(removed.parent);
        assertNull(removed.prev);
        assertNull(removed.next);
        assertNotNull(removed.firstChild);
        assertNull(root.remove(4));

        // validate the structure of the tree after the removal of the node.
        Node n1 = root.find(1);
        assertEquals(1, n1.id);
        assertEquals(0, n1.parent.id);
        assertEquals(2, n1.prev.id);
        assertEquals(3, n1.firstChild.id);
        assertNull(n1.next);

        Node n2 = root.find(2);
        assertEquals(2, n2.id);
        assertEquals(0, n2.parent.id);
        assertEquals(1, n2.next.id);
        assertEquals(6, n2.firstChild.id);
        assertNull(n2.prev);

        Node n3 = root.find(3);
        assertEquals(3, n3.id);
        assertEquals(1, n3.parent.id);
        assertEquals(8, n3.firstChild.id);
        assertNull(n3.prev);
        assertNull(n3.next);

        Node n5 = root.find(5);
        assertEquals(5, n5.id);
        assertEquals(2, n5.parent.id);
        assertEquals(6, n5.prev.id);
        assertNull(n5.firstChild);
        assertNull(n5.next);

        Node n6 = root.find(6);
        assertEquals(6, n6.id);
        assertEquals(2, n6.parent.id);
        assertEquals(5, n6.next.id);
        assertNull(n6.firstChild);
        assertNull(n6.prev);

        Node n7 = root.find(7);
        assertEquals(7, n7.id);
        assertEquals(3, n7.parent.id);
        assertEquals(8, n7.prev.id);
        assertNull(n7.firstChild);
        assertNull(n7.next);

        Node n8 = root.find(8);
        assertEquals(8, n8.id);
        assertEquals(3, n8.parent.id);
        assertEquals(7, n8.next.id);
        assertNull(n8.firstChild);
        assertNull(n8.prev);

        Node n9 = root.find(9);
        assertNull(n9);

        Node n10 = root.find(10);
        assertNull(n10);

        Node n11 = root.find(11);
        assertNull(n11);

        Node n12 = root.find(12);
        assertNull(n12);

        // validate the structure of the removed node
        Node n9_d = removed.find(9);
        assertEquals(9, n9_d.id);
        assertEquals(4, n9_d.parent.id);
        assertEquals(10, n9_d.prev.id);
        assertEquals(12, n9_d.firstChild.id);
        assertNull(n9_d.next);

        Node n10_d = removed.find(10);
        assertEquals(10, n10_d.id);
        assertEquals(4, n10_d.parent.id);
        assertEquals(9, n10_d.next.id);
        assertNull(n10_d.firstChild);
        assertNull(n10_d.prev);

        Node n11_d = removed.find(11);
        assertEquals(11, n11_d.id);
        assertEquals(9, n11_d.parent.id);
        assertEquals(12, n11_d.prev.id);
        assertNull(n11_d.firstChild);
        assertNull(n11_d.next);

        Node n12_d = removed.find(12);
        assertEquals(12, n12_d.id);
        assertEquals(9, n12_d.parent.id);
        assertEquals(11, n12_d.next.id);
        assertNull(n12_d.firstChild);
        assertNull(n12_d.prev);
    }

    @Test
    public void markNodeInTreeExclusive() {
        final Node root = createAndValidate();
        root.find(4).exclusive();

        // validate the structure after the modification
        Node n1 = root.find(1);
        assertEquals(1, n1.id);
        assertEquals(0, n1.parent.id);
        assertEquals(2, n1.prev.id);
        assertEquals(4, n1.firstChild.id);
        assertNull(n1.next);

        Node n2 = root.find(2);
        assertEquals(2, n2.id);
        assertEquals(0, n2.parent.id);
        assertEquals(1, n2.next.id);
        assertEquals(6, n2.firstChild.id);
        assertNull(n2.prev);

        Node n3 = root.find(3);
        assertEquals(3, n3.id);
        assertEquals(4, n3.parent.id);
        assertEquals(10, n3.next.id);
        assertEquals(8, n3.firstChild.id);
        assertNull(n3.prev);

        Node n4 = root.find(4);
        assertEquals(4, n4.id);
        assertEquals(1, n4.parent.id);
        assertEquals(3, n4.firstChild.id);
        assertTrue(n4.exclusive);
        assertNull(n4.next);
        assertNull(n4.prev);

        Node n5 = root.find(5);
        assertEquals(5, n5.id);
        assertEquals(2, n5.parent.id);
        assertEquals(6, n5.prev.id);
        assertNull(n5.firstChild);
        assertNull(n5.next);

        Node n6 = root.find(6);
        assertEquals(6, n6.id);
        assertEquals(2, n6.parent.id);
        assertEquals(5, n6.next.id);
        assertNull(n6.firstChild);
        assertNull(n6.prev);

        Node n7 = root.find(7);
        assertEquals(7, n7.id);
        assertEquals(3, n7.parent.id);
        assertEquals(8, n7.prev.id);
        assertNull(n7.firstChild);
        assertNull(n7.next);

        Node n8 = root.find(8);
        assertEquals(8, n8.id);
        assertEquals(3, n8.parent.id);
        assertEquals(7, n8.next.id);
        assertNull(n8.firstChild);
        assertNull(n8.prev);

        Node n9 = root.find(9);
        assertEquals(9, n9.id);
        assertEquals(4, n9.parent.id);
        assertEquals(10, n9.prev.id);
        assertEquals(12, n9.firstChild.id);
        assertNull(n9.next);

        Node n10 = root.find(10);
        assertEquals(10, n10.id);
        assertEquals(4, n10.parent.id);
        assertEquals(3, n10.prev.id);
        assertEquals(9, n10.next.id);
        assertNull(n10.firstChild);

        Node n11 = root.find(11);
        assertEquals(11, n11.id);
        assertEquals(9, n11.parent.id);
        assertEquals(12, n11.prev.id);
        assertNull(n11.firstChild);
        assertNull(n11.next);

        Node n12 = root.find(12);
        assertEquals(12, n12.id);
        assertEquals(9, n12.parent.id);
        assertEquals(11, n12.next.id);
        assertNull(n12.firstChild);
        assertNull(n12.prev);
    }

    @Test
    public void validateSection5_3_3() {
        final Node root = new TestNode(0);
        root.addChild(new TestNode(1));
        root.find(1).addChild(new TestNode(2));
        root.find(1).addChild(new TestNode(3));
        root.find(3).addChild(new TestNode(4));
        root.find(3).addChild(new TestNode(5));
        root.find(4).addChild(new TestNode(6));

        Node n = root.detach(4);
        root.addChild(n);
        n = root.detach(1);
        root.find(4).addChild(n);

        // validate hierarchy
        Node n1 = root.find(1);
        assertEquals(1, n1.id);
        assertEquals(4, n1.parent.id);
        assertEquals(6, n1.next.id);
        assertEquals(3, n1.firstChild.id);
        assertNull(n1.prev);

        Node n2 = root.find(2);
        assertEquals(2, n2.id);
        assertEquals(1, n2.parent.id);
        assertEquals(3, n2.prev.id);
        assertNull(n2.firstChild);
        assertNull(n2.next);

        Node n3 = root.find(3);
        assertEquals(3, n3.id);
        assertEquals(1, n3.parent.id);
        assertEquals(2, n3.next.id);
        assertEquals(5, n3.firstChild.id);
        assertNull(n3.prev);

        Node n4 = root.find(4);
        assertEquals(4, n4.id);
        assertEquals(0, n4.parent.id);
        assertEquals(1, n4.firstChild.id);
        assertNull(n4.next);
        assertNull(n4.prev);

        Node n5 = root.find(5);
        assertEquals(5, n5.id);
        assertEquals(3, n5.parent.id);
        assertNull(n5.next);
        assertNull(n5.prev);
        assertNull(n5.firstChild);

        Node n6 = root.find(6);
        assertEquals(6, n6.id);
        assertEquals(4, n6.parent.id);
        assertEquals(1, n6.prev.id);
        assertNull(n6.next);
        assertNull(n6.firstChild);

        // Next part of the test...
        root.find(1).exclusive();

        // validate hierarchy
        assertEquals(1, n1.id);
        assertEquals(4, n1.parent.id);
        assertEquals(6, n1.firstChild.id);
        assertNull(n1.next);
        assertNull(n1.prev);

        assertEquals(2, n2.id);
        assertEquals(1, n2.parent.id);
        assertEquals(3, n2.prev.id);
        assertNull(n2.firstChild);
        assertNull(n2.next);

        assertEquals(3, n3.id);
        assertEquals(1, n3.parent.id);
        assertEquals(2, n3.next.id);
        assertEquals(6, n3.prev.id);
        assertEquals(5, n3.firstChild.id);

        assertEquals(4, n4.id);
        assertEquals(0, n4.parent.id);
        assertEquals(1, n4.firstChild.id);
        assertNull(n4.next);
        assertNull(n4.prev);

        assertEquals(5, n5.id);
        assertEquals(3, n5.parent.id);
        assertNull(n5.next);
        assertNull(n5.prev);
        assertNull(n5.firstChild);

        assertEquals(6, n6.id);
        assertEquals(1, n6.parent.id);
        assertEquals(3, n6.next.id);
        assertNull(n6.prev);
        assertNull(n6.firstChild);
    }


    // --------------------------------------------------- Test Support Methods


    Node createAndValidate() {
        return validateDefaultStructure(createDefaultStructure());
    }

    Node createDefaultStructure() {
        final Node root = new TestNode(0);
        root.addChild(new TestNode(1));
        root.addChild(new TestNode(2));
        root.find(1).addChild(new TestNode(3));
        root.find(1).addChild(new TestNode(4));
        root.find(2).addChild(new TestNode(5));
        root.find(2).addChild(new TestNode(6));
        root.find(3).addChild(new TestNode(7));
        root.find(3).addChild(new TestNode(8));
        root.find(4).addChild(new TestNode(9));
        root.find(4).addChild(new TestNode(10));
        root.find(9).addChild(new TestNode(11));
        root.find(9).addChild(new TestNode(12));
        return root;
    }

    Node validateDefaultStructure(final Node root) {
        Node n1 = root.find(1);
        assertEquals(1, n1.id);
        assertEquals(0, n1.parent.id);
        assertEquals(2, n1.prev.id);
        assertEquals(4, n1.firstChild.id);
        assertNull(n1.next);

        Node n2 = root.find(2);
        assertEquals(2, n2.id);
        assertEquals(0, n2.parent.id);
        assertEquals(1, n2.next.id);
        assertEquals(6, n2.firstChild.id);
        assertNull(n2.prev);

        Node n3 = root.find(3);
        assertEquals(3, n3.id);
        assertEquals(1, n3.parent.id);
        assertEquals(4, n3.prev.id);
        assertEquals(8, n3.firstChild.id);
        assertNull(n3.next);

        Node n4 = root.find(4);
        assertEquals(4, n4.id);
        assertEquals(1, n4.parent.id);
        assertEquals(3, n4.next.id);
        assertEquals(10, n4.firstChild.id);
        assertNull(n4.prev);

        Node n5 = root.find(5);
        assertEquals(5, n5.id);
        assertEquals(2, n5.parent.id);
        assertEquals(6, n5.prev.id);
        assertNull(n5.firstChild);
        assertNull(n5.next);

        Node n6 = root.find(6);
        assertEquals(6, n6.id);
        assertEquals(2, n6.parent.id);
        assertEquals(5, n6.next.id);
        assertNull(n6.firstChild);
        assertNull(n6.prev);

        Node n7 = root.find(7);
        assertEquals(7, n7.id);
        assertEquals(3, n7.parent.id);
        assertEquals(8, n7.prev.id);
        assertNull(n7.firstChild);
        assertNull(n7.next);

        Node n8 = root.find(8);
        assertEquals(8, n8.id);
        assertEquals(3, n8.parent.id);
        assertEquals(7, n8.next.id);
        assertNull(n8.firstChild);
        assertNull(n8.prev);

        Node n9 = root.find(9);
        assertEquals(9, n9.id);
        assertEquals(4, n9.parent.id);
        assertEquals(10, n9.prev.id);
        assertEquals(12, n9.firstChild.id);
        assertNull(n9.next);

        Node n10 = root.find(10);
        assertEquals(10, n10.id);
        assertEquals(4, n10.parent.id);
        assertEquals(9, n10.next.id);
        assertNull(n10.firstChild);
        assertNull(n10.prev);

        Node n11 = root.find(11);
        assertEquals(11, n11.id);
        assertEquals(9, n11.parent.id);
        assertEquals(12, n11.prev.id);
        assertNull(n11.firstChild);
        assertNull(n11.next);

        Node n12 = root.find(12);
        assertEquals(12, n12.id);
        assertEquals(9, n12.parent.id);
        assertEquals(11, n12.next.id);
        assertNull(n12.firstChild);
        assertNull(n12.prev);

        return root;
    }


    // --------------------------------------------------------- Nested Classes


    private static final class TestNode extends Node {
        TestNode(final int id) {
            super(id);
        }
    }

}
