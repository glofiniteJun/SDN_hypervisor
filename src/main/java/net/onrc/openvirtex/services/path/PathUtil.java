package net.onrc.openvirtex.services.path;

import java.util.concurrent.ConcurrentHashMap;

import net.onrc.openvirtex.services.path.physicalpath.PhysicalPath;
import net.onrc.openvirtex.services.path.physicalpath.PhysicalPath.PathType;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Created by soulcrime on 2017-07-28.
 */
public class PathUtil {

    private static PathUtil instance;
    private static Logger log = LogManager.getLogger(PathUtil.class.getName());

    public synchronized static PathUtil getInstance() {
        if (PathUtil.instance == null) {
            log.info("Starting PathUtil");

            PathUtil.instance = new PathUtil();
        }
        return PathUtil.instance;
    }

    public int makePathID(int tenantID, int flowID) {
        //return tenantID << 20 | flowID;
    	int oriPathId = tenantID << 20 | flowID << 10;
    	
    	return oriPathId;
    }
    
    /* last two bits are zero for the sub and recovery path */
    public int makePathIDindex(int tenantID, int flowID, PathType pathType) {
    	int oriPathId = makePathID(tenantID, flowID);
    	
    	if(pathType == pathType.MAIN){
    		return oriPathId | 0 << 1 | 0;  // ...00
    	}
    	if(pathType == PathType.RECOVERY){
    		return oriPathId | 0 << 1 | 1;  // ...01
    	}
    	if(pathType == PathType.SUB){
    		return oriPathId | 1 << 1 | 0;  // ...10
    	}
    		
    	return oriPathId;
    }
    
}
