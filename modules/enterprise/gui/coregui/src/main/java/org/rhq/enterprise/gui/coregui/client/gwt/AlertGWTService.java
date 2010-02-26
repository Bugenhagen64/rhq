/*
 * RHQ Management Platform
 * Copyright (C) 2010 Red Hat, Inc.
 * All rights reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */
package org.rhq.enterprise.gui.coregui.client.gwt;

import com.google.gwt.user.client.rpc.RemoteService;
import org.rhq.core.domain.alert.Alert;
import org.rhq.core.domain.authz.Role;
import org.rhq.core.domain.criteria.AlertCriteria;
import org.rhq.core.domain.criteria.RoleCriteria;
import org.rhq.core.domain.util.PageList;

/**
 * @author Ian Springer
 */
public interface AlertGWTService extends RemoteService {
    /**
     * Find all alerts that match the specified criteria.
     *
     * @param criteria the criteria
     *
     * @return all alerts that match the specified criteria
     */
    PageList<Alert> findAlertsByCriteria(AlertCriteria criteria);

    void deleteAlerts(int resourceId, Integer[] alertIds);
}