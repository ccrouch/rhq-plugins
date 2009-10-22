/*
 * RHQ Management Platform
 * Copyright (C) 2005-2009 Red Hat, Inc.
 * All rights reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License, version 2, as
 * published by the Free Software Foundation, and/or the GNU Lesser
 * General Public License, version 2.1, also as published by the Free
 * Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License and the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU General Public License
 * and the GNU Lesser General Public License along with this program;
 * if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */

package org.rhq.plugins.augeas.helper;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * A helper class for easy work with glob patterns.
 * 
 * @author Lukas Krejci
 */
public class Glob {

    private Glob() {
        
    }
    
    public static boolean isWildcard(String globPattern) {
        for(char specialChar : GlobFilter.WILDCARD_CHARS) {
            if (globPattern.indexOf(specialChar) >= 0) return true;
        }
        return false;
    }
    
    /**
     * Returns a fixed size list of matches.
     * 
     * The parent path specifies the "root" from which the glob pattern applies.
     * The glob pattern can span several directories with wildcards present only
     * on the lowest level.
     * The glob pattern is always relative to the specified parent path, even if it denotes
     * an absolute path. In that case, the leading root path is chopped off and the rest is
     * appended to the parent path.
     * 
     * @param parentPath the parent path to start the pattern search
     * @param globPattern the glob pattern to match against
     * @return the list of matches
     */
    public static List<File> match(File parentPath, String globPattern) {
        String rootPortion = rootPortion(globPattern);
        globPattern = globPattern.substring(rootPortion.length());
        
        //now search for the first special character in the patterns
        int specialCharIdx = globPattern.length();
        for(char specialChar : GlobFilter.WILDCARD_CHARS) {
            int idx = globPattern.indexOf(specialChar);
            if (idx >= 0 && specialCharIdx > idx) {
                specialCharIdx = idx;
            }
        }
        
        if (specialCharIdx > 0) {
            //now search for the first path separator preceeding the special char
            int globParentIdx = globPattern.lastIndexOf(File.separatorChar, specialCharIdx);
            if (globParentIdx > 0) {
                //move the parent path down to the nearest parent of the wildcard part of the 
                //glob pattern
                parentPath = new File(parentPath, globPattern.substring(0, globParentIdx));
                globPattern = parentPath.getAbsolutePath() + globPattern.substring(globParentIdx);
            } else {
                globPattern = parentPath.getAbsolutePath() + globPattern;
            }
        } else {
            globPattern = parentPath.getAbsolutePath() + globPattern;            
        }
        
        return Arrays.asList(parentPath.listFiles(new GlobFilter(globPattern)));
    }
    
    public static List<File> matchAll(File parentPath, String... globPattern) {
        return matchAll(parentPath, Arrays.asList(globPattern));
    }
    
    public static List<File> matchAll(File parentPath, List<String> globPatterns) {
        ArrayList<File> matches = new ArrayList<File>();
        for(String p : globPatterns) {
            matches.addAll(match(parentPath, p));
        }
        
        return matches;
    }
    
    public static void exclude(List<File> matches, String globPattern) {
        GlobFilter filter = new GlobFilter(globPattern);
        
        Iterator<File> it = matches.iterator();
        while (it.hasNext()) {
            if (filter.accept(it.next())) {
                it.remove();
            }
        }
    }
    
    public static void excludeAll(List<File> matches, String... globPattern) {
        excludeAll(matches, Arrays.asList(globPattern));
    }    
    
    public static void excludeAll(List<File> matches, List<String> globPatterns) {    
        for(String p : globPatterns) {
            exclude(matches, p);
        }
    }

    private static String rootPortion(String path) {
        for (File root : File.listRoots()) {
            if (path.startsWith(root.getPath())) {
                return root.getPath();
            }
        }
        
        return "";
    }
}
