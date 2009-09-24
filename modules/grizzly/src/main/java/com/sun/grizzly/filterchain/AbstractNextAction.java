/*
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2007-2008 Sun Microsystems, Inc. All rights reserved.
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
 *
 */

package com.sun.grizzly.filterchain;

import java.util.Collections;
import java.util.List;

/**
 * Base implementation of {@link NextAction}
 *
 * @see NextAction
 * 
 * @author Alexey Stashok
 */
abstract class AbstractNextAction implements NextAction {

    protected int type;
    protected List<Filter> filters;
    private List<Filter> unmodifiableFiltersView;
    protected int nextFilterIdx;

    protected AbstractNextAction(int type) {
        this.type = type;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int type() {
        return type;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Filter> getFilters() {
        return unmodifiableFiltersView;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getNextFilterIdx() {
        return nextFilterIdx;
    }

    /**
     * Set the {@link Filter} list.
     * 
     * @param nextFilters the {@link Filter} list.
     */
    void setFilters(List<Filter> filters) {
        this.filters = filters;
        this.unmodifiableFiltersView = (filters != null) ? Collections.unmodifiableList(filters) : null;
    }
    
    /**
     * Sets index of the {@link Filter} in
     * {@link NextAction#getFilters()} list, which should be executed next.
     *
     * @param nextFilterIdx index of the {@link Filter} in
     * {@link NextAction#getFilters()} list, which should be executed next.
     */
    void setNextFilterIdx(int nextFilterIdx) {
        this.nextFilterIdx = nextFilterIdx;
    }
}
