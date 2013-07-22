/*
 *
 *  * RHQ Management Platform
 *  * Copyright (C) 2005-2012 Red Hat, Inc.
 *  * All rights reserved.
 *  *
 *  * This program is free software; you can redistribute it and/or modify
 *  * it under the terms of the GNU General Public License, version 2, as
 *  * published by the Free Software Foundation, and/or the GNU Lesser
 *  * General Public License, version 2.1, also as published by the Free
 *  * Software Foundation.
 *  *
 *  * This program is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  * GNU General Public License and the GNU Lesser General Public License
 *  * for more details.
 *  *
 *  * You should have received a copy of the GNU General Public License
 *  * and the GNU Lesser General Public License along with this program;
 *  * if not, write to the Free Software Foundation, Inc.,
 *  * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *
 */

package org.rhq.enterprise.server.measurement;

import java.util.List;
import java.util.Set;

import javax.ejb.Local;

import org.rhq.core.domain.measurement.MeasurementAggregate;
import org.rhq.core.domain.measurement.MeasurementDataNumeric;
import org.rhq.core.domain.measurement.composite.MeasurementDataNumericHighLowComposite;
import org.rhq.server.metrics.domain.AggregateNumericMetric;

/**
 * This SLSB is (hopefully) just a temporary wrapper over {@link org.rhq.server.metrics.MetricsServer MetricsServer}.
 * This EJB is being used to delegate calls to MetricsServer since managed beans a la CDI
 * cannot be used yet. Once things are straightened out and we can use CDI, then this usage of this EJB
 * will be directly replaced with use of MetricsServer.
 *
 * @author John Sanda
 */
@Local
public interface MetricsManagerLocal {

    void addNumericData(Set<MeasurementDataNumeric> data, Runnable callback);

    Iterable<AggregateNumericMetric> calculateAggregates();

    MeasurementDataNumeric findLatestValueForResource(int scheduleId);

    Iterable<MeasurementDataNumericHighLowComposite> findDataForResource(int scheduleId, long beginTime, long endTime);

    Iterable<MeasurementDataNumericHighLowComposite> findDataForResource(int scheduleId, long beginTime, long endTime, int numberOfBuckets);

    Iterable<MeasurementDataNumericHighLowComposite> findDataForResourceGroup(List<Integer> scheduleIds,
        long beginTime, long endTime);

    Iterable<MeasurementDataNumericHighLowComposite> findDataForResourceGroup(List<Integer> scheduleIds,
        long beginTime, long endTime, int numberOfBuckets);

    MeasurementAggregate getSummaryAggregate(int scheduleId, long beginTime, long endTime);

    MeasurementAggregate getSummaryAggregate(List<Integer> scheduleIds, long beginTime, long endTime);

}