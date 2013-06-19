/*
 * RHQ Management Platform
 * Copyright 2013, Red Hat Middleware LLC, and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
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

package org.rhq.server.metrics.migrator;

import org.apache.commons.lang.time.StopWatch;

/**
 * @author Stefan Negrea
 *
 */
public class Telemetry {
    private StopWatch generalTimer;
    private StopWatch migrationTimer;

    public Telemetry() {
        this.generalTimer = new StopWatch();
        this.migrationTimer = new StopWatch();
    }

    public StopWatch getGeneralTimer() {
        return generalTimer;
    }

    public StopWatch getMigrationTimer() {
        return migrationTimer;
    }

    public long getMigrationTime() {
        return migrationTimer.getTime();
    }

    public long getGeneralTime() {
        return generalTimer.getTime();
    }

    public long getNonMigrationTime() {
        return this.getGeneralTime() - this.getMigrationTime();
    }
}
