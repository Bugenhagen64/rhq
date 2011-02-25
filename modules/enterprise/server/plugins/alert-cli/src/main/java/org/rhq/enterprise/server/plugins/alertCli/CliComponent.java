/*
 * RHQ Management Platform
 * Copyright (C) 2005-2011 Red Hat, Inc.
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

package org.rhq.enterprise.server.plugins.alertCli;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.rhq.core.domain.alert.AlertDefinition;
import org.rhq.core.domain.alert.notification.AlertNotification;
import org.rhq.core.domain.auth.Subject;
import org.rhq.core.domain.configuration.Configuration;
import org.rhq.core.domain.configuration.Property;
import org.rhq.core.domain.configuration.PropertyList;
import org.rhq.core.domain.configuration.PropertyMap;
import org.rhq.core.domain.configuration.PropertySimple;
import org.rhq.core.domain.content.PackageCategory;
import org.rhq.core.domain.content.PackageType;
import org.rhq.core.domain.content.PackageVersion;
import org.rhq.core.domain.criteria.AlertDefinitionCriteria;
import org.rhq.core.domain.criteria.Criteria.Restriction;
import org.rhq.core.domain.criteria.PackageVersionCriteria;
import org.rhq.core.domain.resource.composite.DisambiguationReport;
import org.rhq.core.domain.util.DisambiguationReportRenderer;
import org.rhq.core.domain.util.PageControl;
import org.rhq.core.domain.util.PageList;
import org.rhq.core.util.IntExtractor;
import org.rhq.enterprise.server.alert.AlertDefinitionManagerLocal;
import org.rhq.enterprise.server.alert.AlertNotificationManagerLocal;
import org.rhq.enterprise.server.auth.SubjectManagerLocal;
import org.rhq.enterprise.server.content.ContentManagerLocal;
import org.rhq.enterprise.server.content.RepoManagerLocal;
import org.rhq.enterprise.server.plugin.pc.ControlFacet;
import org.rhq.enterprise.server.plugin.pc.ControlResults;
import org.rhq.enterprise.server.plugin.pc.ServerPluginComponent;
import org.rhq.enterprise.server.plugin.pc.ServerPluginContext;
import org.rhq.enterprise.server.resource.ResourceManagerLocal;
import org.rhq.enterprise.server.resource.disambiguation.DefaultDisambiguationUpdateStrategies;
import org.rhq.enterprise.server.util.LookupUtil;
import org.rhq.enterprise.server.xmlschema.generated.serverplugin.alert.AlertPluginDescriptorType;

/**
 * The plugin component for controlling the CLI alerts.
 *
 * @author Lukas Krejci
 */
public class CliComponent implements ServerPluginComponent, ControlFacet {

    //TODO the package type definition could be defined somewhere accessible
    //server-wide if we decide to use it in more places than just this alert sender
    
    public static final String PACKAGE_TYPE_NAME = "__SERVER_SIDE_CLI_SCRIPT";
    public static final String PACKAGE_TYPE_DESCRIPTION = 
        "A CLI script running on the server-side.";
    public static final String PACKAGE_TYPE_DISPLAY_NAME = "Server-side CLI Script";
    
    private static final String CONTROL_CHECK_ALERTS_VALIDITY = "checkAlertsValidity";
    private static final String CONTROL_REASSIGN_ALERTS = "reassignAlerts";
    
    private static final String PROP_ALERT_DEFINITION_NAME = "alertDefinitionName";
    private static final String PROP_RESOURCE_PATH = "resourcePath";
    private static final String PROP_RESOURCE_ID = "resourceId";
    private static final String PROP_MISSING_USERS = "missingUsers";
    private static final String PROP_MISSING_SCRIPTS = "missingScripts";
    private static final String PROP_ALERT_DEFINITION = "alertDefinition";
    private static final String PROP_ALERT_DEFINITION_ID = "alertDefinitionId";
    private static final String PROP_USER_NAME = "userName";
    private static final String PROP_ALERT_DEF_IDS = "alertDefIds";
    private static final String PROP_SCRIPT_TIMEOUT = "scriptTimeout";
    
    private String pluginName;
    private PackageType packageType;
    private int scriptTimeout;
    
    private SubjectManagerLocal subjectManager;
    
    public void initialize(ServerPluginContext context) throws Exception {
        pluginName = ((AlertPluginDescriptorType)context.getPluginEnvironment().getPluginDescriptor()).getShortName();
        
        subjectManager = LookupUtil.getSubjectManager();
        ContentManagerLocal cm = LookupUtil.getContentManager();
        
        packageType = cm.findPackageType(subjectManager.getOverlord(), null, PACKAGE_TYPE_NAME);
        
        if (packageType == null) {
            packageType = new PackageType(PACKAGE_TYPE_NAME, null);
            packageType.setCategory(PackageCategory.EXECUTABLE_SCRIPT);
            packageType.setDescription(PACKAGE_TYPE_DESCRIPTION);
            packageType.setDisplayName(PACKAGE_TYPE_DISPLAY_NAME);
            packageType.setSupportsArchitecture(false);
            packageType.setCreationData(false);
            packageType.setDeploymentConfigurationDefinition(null);
            packageType.setDiscoveryInterval(-1);
            packageType.setPackageExtraPropertiesDefinition(null);
            
            packageType = cm.persistServersidePackageType(packageType);
        }
        
        String timeoutValue = context.getPluginConfiguration() == null ? "60" : context.getPluginConfiguration().getSimpleValue(PROP_SCRIPT_TIMEOUT, "60");
        
        scriptTimeout = Integer.parseInt(timeoutValue);
    }

    public PackageType getScriptPackageType() {
        return packageType;
    }
    
    public int getScriptTimeout() {
        return scriptTimeout;
    }
    
    public void start() {
    }

    public void stop() {
    }

    public void shutdown() {
    }

    public ControlResults invoke(String name, Configuration parameters) {
        ControlResults results = new ControlResults();
        
        try {
            if (CONTROL_CHECK_ALERTS_VALIDITY.equals(name)) {
                checkAlertsValidity(results, parameters);
            } else if (CONTROL_REASSIGN_ALERTS.equals(name)) {
                reassignAlerts(results, parameters);
            }
        } catch (Exception e) {
            results.setError(e);
        }
        
        return results;
    }
    
    private void checkAlertsValidity(ControlResults results, Configuration parameters) {
        List<AlertNotification> allCliNotifications = getAllCliNotifications(null);
               
        Configuration resConfig = results.getComplexResults();
        
        PropertyList missingUsersList = new PropertyList(PROP_MISSING_USERS);
        resConfig.put(missingUsersList);
        
        List<AlertNotification> invalidNotifs = getCliNotificationsWithInvalidUser(allCliNotifications);
        convertNotificationsToInvalidAlertDefResults(missingUsersList, invalidNotifs);

        PropertyList missingScriptsList = new PropertyList(PROP_MISSING_SCRIPTS);
        resConfig.put(missingScriptsList);
        
        invalidNotifs = getCliNotificationsWithInvalidPackage(allCliNotifications);
        convertNotificationsToInvalidAlertDefResults(missingScriptsList, invalidNotifs);
        
        //ok, now we have to obtain the resource paths. doing it out of the above loop reduces the number
        //of server roundtrips
        
        List<Property> allMissing = new ArrayList<Property>();
        allMissing.addAll(missingUsersList.getList());
        allMissing.addAll(missingScriptsList.getList());
        
        if (allMissing.size() > 0) {
            ResourceManagerLocal resourceManager = LookupUtil.getResourceManager();
            
            List<DisambiguationReport<Property>> disambiguated = resourceManager.disambiguate(allMissing, 
                new IntExtractor<Property>() {
                    public int extract(Property object) {
                        PropertyMap map = (PropertyMap) object;
                        return map.getSimple(PROP_RESOURCE_ID).getIntegerValue();
                    };
                }, 
                DefaultDisambiguationUpdateStrategies.KEEP_ALL_PARENTS);
            
            DisambiguationReportRenderer renderer = new DisambiguationReportRenderer();
            
            for(DisambiguationReport<Property> r : disambiguated) {
                PropertyMap map = (PropertyMap) r.getOriginal();
                
                String resourcePath = renderer.render(r);
                
                map.put(new PropertySimple(PROP_RESOURCE_PATH, resourcePath));
            }
        }
        
    }
    
    private void reassignAlerts(ControlResults results, Configuration parameters) {
        PropertySimple userNameProp = parameters.getSimple(PROP_USER_NAME);
        PropertySimple alertDefIdsProp = parameters.getSimple(PROP_ALERT_DEF_IDS);
        
        if (userNameProp == null || userNameProp.getStringValue() == null || userNameProp.getStringValue().trim().length() == 0) {
            throw new IllegalArgumentException("User name not specified.");
        }
        
        String userName = userNameProp.getStringValue();
        SubjectManagerLocal subjectManager = LookupUtil.getSubjectManager();
        Subject subject = subjectManager.getSubjectByName(userName);
        
        if (subject == null) {
            throw new IllegalArgumentException("User '" + userName + "' doesn't exist.");
        }
        
        //now get the list of the alert notifications to update
        List<AlertNotification> notifsToReAssign = null;
        if (alertDefIdsProp == null || alertDefIdsProp.getStringValue() == null || alertDefIdsProp.getStringValue().trim().length() == 0) {
            notifsToReAssign = getCliNotificationsWithInvalidUser(getAllCliNotifications(null));
        } else {
            List<Integer> alertDefIds = asIdList(alertDefIdsProp.getStringValue().split("\\s*,\\s*"));
            List<AlertDefinition> defs = getAlertDefinitionsWithCliNotifications(alertDefIds);
            
            notifsToReAssign = new ArrayList<AlertNotification>();
            for(AlertDefinition def : defs) {
                for(AlertNotification cliNotif : getCliNotifications(def.getAlertNotifications())) {
                    notifsToReAssign.add(cliNotif);
                }
            }
        }
        
        AlertNotificationManagerLocal notificationManager = LookupUtil.getAlertNotificationManager();

        List<Integer> notifIds = asIdList(notifsToReAssign);
        Map<String, String> updates = Collections.singletonMap(CliSender.PROP_USER_ID, Integer.toString(subject.getId()));
        notificationManager.massReconfigure(notifIds, updates);
    }
    
    private List<AlertNotification> getCliNotifications(List<AlertNotification> notifications) {
        ArrayList<AlertNotification> ret = new ArrayList<AlertNotification>();
        for(AlertNotification n : notifications) {
            if (pluginName.equals(n.getSenderName())) {
                ret.add(n);
            }
        }
        
        return ret;
    }
    
    private List<AlertDefinition> getAlertDefinitionsWithCliNotifications(Collection<Integer> alertDefIds) {
        SubjectManagerLocal subjectManager = LookupUtil.getSubjectManager();
        AlertDefinitionManagerLocal manager = LookupUtil.getAlertDefinitionManager();

        Subject overlord = subjectManager.getOverlord();

        AlertDefinitionCriteria criteria = new AlertDefinitionCriteria();
        criteria.addFilterNotificationNames(pluginName);
        criteria.setPageControl(PageControl.getUnlimitedInstance());
        criteria.fetchAlertNotifications(true);
        if (alertDefIds != null) {
            criteria.addFilterIds(alertDefIds.toArray(new Integer[alertDefIds.size()]));
        }
        
        return manager.findAlertDefinitionsByCriteria(overlord, criteria);
    }
    
    private List<AlertNotification> getAllCliNotifications(Collection<Integer> alertDefIds) {
        List<AlertNotification> ret = new ArrayList<AlertNotification>();
        
        List<AlertDefinition> defs = getAlertDefinitionsWithCliNotifications(alertDefIds);
        
        for(AlertDefinition def : defs) {
            List<AlertNotification> notifications = def.getAlertNotifications();
            
            ret.addAll(getCliNotifications(notifications));
        }
        
        return ret;
    }
    
    private List<AlertNotification> getCliNotificationsWithInvalidUser(List<AlertNotification> allNotifications) {
        List<AlertNotification> ret = new ArrayList<AlertNotification>();
        
        SubjectManagerLocal subjectManager = LookupUtil.getSubjectManager();
        
        for(AlertNotification cliNotification : allNotifications) {
            
            Subject checkSubject = null;
            
            PropertySimple subjectIdProperty = cliNotification.getConfiguration().getSimple(CliSender.PROP_USER_ID);
            if (subjectIdProperty != null) {                
                int subjectId = subjectIdProperty.getIntegerValue();
                
                checkSubject = subjectManager.getSubjectById(subjectId);
            }
            
            if (checkSubject == null) {
                ret.add(cliNotification);
            }
        }
        
        return ret;
    }
    
    private List<AlertNotification> getCliNotificationsWithInvalidPackage(List<AlertNotification> allNotifications) {
        List<AlertNotification> ret = new ArrayList<AlertNotification>();
        
        ContentManagerLocal contentManager = LookupUtil.getContentManager();
        SubjectManagerLocal subjectManager = LookupUtil.getSubjectManager();
        
        Subject overlord = subjectManager.getOverlord();        
        
        PackageVersionCriteria crit = new PackageVersionCriteria();
        crit.setRestriction(Restriction.COUNT_ONLY);
        
        for(AlertNotification cliNotification : allNotifications) {
            
            int count = 0;
            
            String packageId = cliNotification.getConfiguration().getSimpleValue(CliSender.PROP_PACKAGE_ID, null);
            if (packageId != null) {
                crit.addFilterPackageId(Integer.valueOf(packageId));
                
                PageList<PackageVersion> res = contentManager.findPackageVersionsByCriteria(overlord, crit);
                count = res.getTotalSize();
            }            
            
            if (count == 0) {
                ret.add(cliNotification);
            }
        }
        
        return ret;
    }
    
    private void convertNotificationsToInvalidAlertDefResults(PropertyList results, List<AlertNotification> invalidNotifications) {
        Set<AlertDefinition> processedDefs = new HashSet<AlertDefinition>();
        
        for(AlertNotification cliNotification : invalidNotifications) {
            AlertDefinition def = cliNotification.getAlertDefinition();
            
            if (!processedDefs.add(def)) {
                //skip this definition - it has more than one incorrect notifs and we already
                //included it in the output.
                continue;
            }
                        
            PropertyMap alertDefinitionMap = new PropertyMap(PROP_ALERT_DEFINITION);
            
            alertDefinitionMap.put(new PropertySimple(PROP_ALERT_DEFINITION_ID, def.getId()));
            alertDefinitionMap.put(new PropertySimple(PROP_ALERT_DEFINITION_NAME, def.getName()));
            alertDefinitionMap.put(new PropertySimple(PROP_RESOURCE_ID, def.getResource().getId()));
            
            results.add(alertDefinitionMap);
        }
    }
    
    private List<Integer> asIdList(String... ids) {
        List<Integer> ret = new ArrayList<Integer>();
        for(String id : ids) {
            ret.add(Integer.parseInt(id));
        }
        
        return ret;
    }
    
    private List<Integer> asIdList(Collection<AlertNotification> notifs) {
        ArrayList<Integer> ret = new ArrayList<Integer>();
        
        for(AlertNotification n : notifs) {
            ret.add(n.getId());
        }
        
        return ret;
    }
}
