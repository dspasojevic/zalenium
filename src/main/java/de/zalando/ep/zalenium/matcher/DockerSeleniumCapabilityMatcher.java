package de.zalando.ep.zalenium.matcher;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.openqa.grid.internal.TestSlot;
import org.openqa.grid.internal.utils.DefaultCapabilityMatcher;
import org.openqa.grid.selenium.proxy.DefaultRemoteProxy;
import org.openqa.selenium.remote.BrowserType;
import org.openqa.selenium.remote.CapabilityType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.zalando.ep.zalenium.proxy.DockeredSeleniumStarter;

public class DockerSeleniumCapabilityMatcher extends DefaultCapabilityMatcher {
    private static final List<String> ZALENIUM_CUSTOM_CAPABILITIES_NO_PREFIX = Arrays.asList(
            ZaleniumCapabilityType.TEST_NAME_NO_PREFIX,
            ZaleniumCapabilityType.BUILD_NAME_NO_PREFIX,
            ZaleniumCapabilityType.IDLE_TIMEOUT_NO_PREFIX,
            ZaleniumCapabilityType.SCREEN_RESOLUTION_NO_PREFIX,
            ZaleniumCapabilityType.RESOLUTION_NO_PREFIX,
            ZaleniumCapabilityType.SCREEN_RESOLUTION_DASH_NO_PREFIX,
            ZaleniumCapabilityType.RECORD_VIDEO_NO_PREFIX,
            ZaleniumCapabilityType.TIME_ZONE_NO_PREFIX);

    private final Logger logger = LoggerFactory.getLogger(DockerSeleniumCapabilityMatcher.class.getName());
    private DefaultRemoteProxy proxy;

    public DockerSeleniumCapabilityMatcher(DefaultRemoteProxy defaultRemoteProxy) {
        super();
        proxy = defaultRemoteProxy;
    }

    @Override
    public boolean matches(Map<String, Object> nodeCapability, Map<String, Object> requestedCapability) {
        logger.debug(String.format("Validating %s in node with capabilities %s", requestedCapability,
                nodeCapability));

        if (!requestedCapability.containsKey(CapabilityType.BROWSER_NAME)) {
            logger.debug(String.format("%s Capability %s does not contain %s key, a docker-selenium " +
                    "node cannot be started without it", proxy.getId(), requestedCapability, CapabilityType.BROWSER_NAME));
            return false;
        }

        // DockerSeleniumRemoteProxy part
        if (super.matches(nodeCapability, requestedCapability)) {
            // Prefix Zalenium custom capabilities here (both node and requested)
            prefixZaleniumCustomCapabilities(nodeCapability);
            prefixZaleniumCustomCapabilities(requestedCapability);

            boolean screenResolutionMatches = isScreenResolutionMatching(nodeCapability, requestedCapability);
            boolean timeZoneCapabilityMatches = isTimeZoneMatching(nodeCapability, requestedCapability);
            return screenResolutionMatches && timeZoneCapabilityMatches;
        }
        return false;
    }

    private void prefixZaleniumCustomCapabilities(Map<String, Object> capabilities) {
        for (String zaleniumCustomCapability : ZALENIUM_CUSTOM_CAPABILITIES_NO_PREFIX) {
            if (capabilities.containsKey(zaleniumCustomCapability)) {
                String prefixedCapability = ZaleniumCapabilityType.CUSTOM_CAPABILITY_PREFIX.concat(zaleniumCustomCapability);
                capabilities.put(prefixedCapability, capabilities.get(zaleniumCustomCapability));
                capabilities.remove(zaleniumCustomCapability);
            }
        }
    }

    // Cannot use Collectors.toMap() because it fails when there are null values.
    private Map<String, Object> copyMap(Map<String, Object> mapToCopy) {
        Map<String, Object> copiedMap = new HashMap<>();
        for (Map.Entry<String, Object> entry : mapToCopy.entrySet()) {
            copiedMap.put(entry.getKey(), entry.getValue());
        }
        return copiedMap;
    }

    private boolean isScreenResolutionMatching(Map<String, Object> nodeCapability, Map<String, Object> requestedCapability) {
        boolean screenResolutionCapabilityMatches = true;
        boolean screenSizeCapabilityIsRequested = false;

        List<String> screenResolutionNames = Arrays.asList(ZaleniumCapabilityType.SCREEN_RESOLUTION,
                ZaleniumCapabilityType.RESOLUTION, ZaleniumCapabilityType.SCREEN_RESOLUTION_DASH);

        for (String screenResolutionName : screenResolutionNames) {
            if (requestedCapability.containsKey(screenResolutionName)) {
                screenSizeCapabilityIsRequested = true;
                screenResolutionCapabilityMatches = nodeCapability.containsKey(screenResolutionName) &&
                        requestedCapability.get(screenResolutionName).equals(nodeCapability.get(screenResolutionName));
            }
        }

        /*
            This node has a screen size different from the default/configured one,
            and no special screen size was requested...
            then this validation prevents requests using nodes that were created with specific screen sizes
         */
        String defaultScreenResolution = String.format("%sx%s",
                DockeredSeleniumStarter.getConfiguredScreenSize().getWidth(),
                DockeredSeleniumStarter.getConfiguredScreenSize().getHeight());
        String nodeScreenResolution = nodeCapability.get(ZaleniumCapabilityType.SCREEN_RESOLUTION).toString();
        if (!screenSizeCapabilityIsRequested && !defaultScreenResolution.equalsIgnoreCase(nodeScreenResolution)) {
            screenResolutionCapabilityMatches = false;
        }
        return screenResolutionCapabilityMatches;
    }

    private boolean isTimeZoneMatching(Map<String, Object> nodeCapability, Map<String, Object> requestedCapability) {
        boolean timeZoneCapabilityMatches;

        String defaultTimeZone = DockeredSeleniumStarter.getConfiguredTimeZone().getID();
        String nodeTimeZone = nodeCapability.get(ZaleniumCapabilityType.TIME_ZONE).toString();

        /*
            If a time zone is not requested in the capabilities,
            and this node has a different time zone from the default/configured one...
            this will prevent that a request without a time zone uses a node created with a specific time zone
         */
        if (requestedCapability.containsKey(ZaleniumCapabilityType.TIME_ZONE)) {
            timeZoneCapabilityMatches = nodeCapability.containsKey(ZaleniumCapabilityType.TIME_ZONE) &&
                    requestedCapability.get(ZaleniumCapabilityType.TIME_ZONE).equals(nodeCapability.get(ZaleniumCapabilityType.TIME_ZONE));
        } else {
            timeZoneCapabilityMatches = defaultTimeZone.equalsIgnoreCase(nodeTimeZone);
        }

        return timeZoneCapabilityMatches;
    }

}
