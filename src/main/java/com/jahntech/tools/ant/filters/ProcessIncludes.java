/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package com.jahntech.tools.ant.filters;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.filters.BaseFilterReader;
import org.apache.tools.ant.filters.BaseParamFilterReader;
import org.apache.tools.ant.filters.ChainableReader;
import org.apache.tools.ant.types.Parameter;
import org.apache.tools.ant.util.FileUtils;


/**
 * Process include statements, if any, in the data.
 * <p>
 * Example:<br>
 * <pre>&lt;processincludes/&gt;</pre>
 * Or:
 * <pre>&lt;filterreader
 *    classname=&quot;com.jahntech.tools.ant.filters.ProcessIncludes&quot;/&gt;</pre>
 *
 */
public final class ProcessIncludes
    extends BaseParamFilterReader
    implements ChainableReader {

	private static final String DEFAULT_PATTERN = "^##include \"(.*)\"$";
	private static final int DEFAULT_PATTERN_GROUP_FILENAME = 1;
	private static final String DEFAULT_LINE_PREFIX = "";
	private static final String DEFAULT_LINE_SUFFIX = "";
	
	private static final String KEY_LINE_PREFIX = "prefix";
	private static final String KEY_LINE_SUFFIX = "suffix";
	private static final String KEY_PATTERN = "pattern";
	private static final String KEY_PATTERN_GROUP_FILENAME = "group";

	private static final String TYPE_SETTING = "setting";
	private static final String TYPE_SEARCHDIR = "searchdir";
	private static final String TYPE_PROPERTIESFILE = "propertiesfile";
	
    /** Hashtable to hold the settings. */
    private Hashtable hash = new Hashtable();

    /** Array list to hold the searchdirs. */
    private ArrayList dirs = new ArrayList();
    
    /** Data that must be read from, if not null. */
    private String queuedData = null;

    
    /**
     * Constructor for "dummy" instances.
     *
     * @see BaseFilterReader#BaseFilterReader()
     */
    public ProcessIncludes() {
        super();
    }

    /**
     * Creates a new filtered reader.
     *
     * @param in A Reader object providing the underlying stream.
     *           Must not be <code>null</code>.
     */
    public ProcessIncludes(final Reader in) {
        super(in);
    }
   
    /**
     * Adds a setting element to the list of settings
     *
     * @param serchdir The token to add to the map of replacements.
     *              Must not be <code>null</code>.
     */
    public void addConfiguredSetting(final Setting setting) {
        hash.put(setting.getKey(), setting.getValue());
    }
    
    /**
     * Adds a searchdir element to the list of dirs to check.
     *
     * @param searchdir The searchdir to add to the list of dirs.
     *              Must not be <code>null</code>.
     */
    public void addConfiguredSearchdir(final Searchdir searchdir) {
        dirs.add(searchdir.getValue());
    }

    /**
     * Returns the next character in the filtered stream. The original
     * stream is first read in fully, and the included files are read in.
     * The results of this expansion are then queued so they can be read
     * character-by-character.
     *
     * @return the next character in the resulting stream, or -1
     * if the end of the resulting stream has been reached
     *
     * @exception IOException if the underlying stream throws an IOException
     * during reading
     */
    public int read() throws IOException {

        if (!getInitialized()) {
            initialize();
            setInitialized(true);
        } 
        
        int ch = -1;

        if (queuedData != null && queuedData.length() == 0) {
            queuedData = null;
        }

        if (queuedData != null) {
            ch = queuedData.charAt(0);
            queuedData = queuedData.substring(1);
            if (queuedData.length() == 0) {
                queuedData = null;
            }
        } else {
            queuedData = readFully();
            if (queuedData == null || queuedData.length() == 0) {
                ch = -1;
            } else {
                queuedData = parseData(queuedData);

//                StringBuffer sb = new StringBuffer();
//                Enumeration settingKeys = hash.keys();
//                while (settingKeys.hasMoreElements()) {
//					String key = (String) settingKeys.nextElement();
//					sb.append(key).append(" = ").append(getSetting(key)).append("\n");
//				}
//                for (int i = 0; i < dirs.size(); i++) {
//                	sb.append("searchdir = ").append((String) dirs.get(i)).append("\n");
//                }
//                queuedData =  sb.toString() + queuedData;

                return read();
            }
        }
        return ch;
    }
    
  
    /**
     * Returns the File object representing the search file defined.
     * All defined search paths are scanned and the first to contain
     * a file where the name fits will be taken. Directories will be  
     * scanned in the order in which they were defined.
     *
     * @return the File object representing the include file 
     *
     * @exception FileNotFoundException if none of the defined search paths
     * contains the include file
     */

	private File getIncludeFile(String fileName) throws FileNotFoundException {
		for (int i = 0; i < dirs.size(); i++) {
			String inclDir = (String) dirs.get(i);
			File inclFile = new File(inclDir + System.getProperty("file.separator") + fileName);
			if (inclFile.exists())
				return inclFile;
		}
    	throw new FileNotFoundException();
    }
 
    /**
     * Returns the string containing the parsed data. 
     * The actual processing of included files happens here.
     *
     * @return the String containing the content from included files. 
     *
     * @exception IOException if there are problems while reading 
     * one of the include files.
     */
	private String parseData(String queuedData) throws IOException {
    	Pattern p = Pattern.compile(DEFAULT_PATTERN, Pattern.MULTILINE);
    	Matcher m = p.matcher(queuedData);

    	 StringBuffer sb = new StringBuffer();
    	 BufferedReader reader = null;

    	 while (m.find()) {
    		 File inclFile = getIncludeFile(m.group(new Integer(getSetting(KEY_PATTERN_GROUP_FILENAME)).intValue()));
    		 reader = new BufferedReader(new FileReader(inclFile));

    		 String lineFromFile = null;
    		 StringBuffer fileContent = new StringBuffer();

    		 // repeat until all lines are read
    		 while ((lineFromFile = reader.readLine()) != null) {
    			 fileContent.append(getSetting(KEY_LINE_PREFIX)).append(lineFromFile).
    			 	append(getSetting(KEY_LINE_SUFFIX)).append(System.getProperty("line.separator"));
    		 }
    		 // The logic that reads the include file, appends an additional
    		 // new line character, which needs to be removed again.
    		 fileContent.deleteCharAt((fileContent.length())-1);
    		 m.appendReplacement(sb, fileContent.toString());
    	 }
    	 m.appendTail(sb);
    	 return sb.toString();
	}

    /**
     * Creates a new ProcessIncludes filter using the passed in
     * Reader for instantiation.
     *
     * @param rdr A Reader object providing the underlying stream.
     *            Must not be <code>null</code>.
     *
     * @return a new filter based on this configuration, but filtering
     *         the specified reader
     */
    public Reader chain(final Reader rdr) {
        ProcessIncludes newFilter = new ProcessIncludes(rdr);
        newFilter.setProject(getProject());
        newFilter.setSettings(getSettings());
        newFilter.setSearchdirs(getSearchdirs());
        newFilter.setInitialized(true);
        return newFilter;
    }
    
    /**
     * Returns properties from a specified properties file.
     *
     * @param fileName The file to load properties from.
     */
    private Properties getPropertiesFromFile (String fileName) {
        FileInputStream in = null;
        Properties props = new Properties();
        try {
            in = new FileInputStream(fileName);
            props.load(in);
        } catch (IOException ioe) {
            ioe.printStackTrace();
        } finally {
            FileUtils.close(in);
        }

        return props;
    }

    
    /**
     * Adds dir to the list of search dirs 
     *
     * @exception BuildException if a NULL or an empty string is 
     * provided as search dir.
     */
    private void addSearchdir(String searchDir) {
    	if (searchDir == null)
    		throw new BuildException("Search dir must not be null");
    	if (searchDir.length() == 0)
    		throw new BuildException("Search dir must not be an empty string");
    	Searchdir searchdir = new Searchdir();
    	searchdir.setValue(searchDir);
    	addConfiguredSearchdir(searchdir);
    }


    /**
     * Adds setting 
     *
     * @exception BuildException if a NULL or an empty string is 
     * provided as key for the setting.
     */
    private void addSetting(String key, String value) {
    	if (key == null)
    		throw new BuildException("Key for a setting must not be null");
    	if (key.length() == 0)
    		throw new BuildException("Key for a setting must not be an empty string");
    	Setting setting= new Setting();
    	setting.setKey(key);
    	setting.setValue(value);
    	addConfiguredSetting(setting);
    }

    
    /**
     * Sets the map of settings.
     *
     * @param hash A map (String->String) of token keys to replacement
     * values. Must not be <code>null</code>.
     */
    private void setSettings(final Hashtable hash) {
        this.hash = hash;
    }

    
    /**
     * Make use of default setting, if nothing has been provided.
     *
     * @param hash A map (String->String) of token keys to replacement
     * values. Must not be <code>null</code>.
     */
    private void applyDefaultSeting(String key, String value) {
    	if (!hash.containsKey(key))
    		hash.put(key, value);
    }
   
    
    /**
     * Returns the map of settings.
     *
     * @return a map (String->String) of token keys to replacement
     * values
     */
    private Hashtable getSettings() {
 	   // Apply default settings where appropriate
    	applyDefaultSeting(KEY_LINE_PREFIX, DEFAULT_LINE_PREFIX);
        applyDefaultSeting(KEY_LINE_SUFFIX, DEFAULT_LINE_SUFFIX);
        applyDefaultSeting(KEY_PATTERN, DEFAULT_PATTERN);
        applyDefaultSeting(KEY_PATTERN_GROUP_FILENAME, new Integer(DEFAULT_PATTERN_GROUP_FILENAME).toString());
        return hash;
    }
    
    
    /**
     * Returns the value of a single setting.
     *
     * @return a map (String->String) of token keys to replacement
     * values
     */
    private String getSetting(String key) {
    	if (hash.containsKey(key))
    		return (String) hash.get(key);
    	else
    		return null;
    }

    
    /**
     * Sets the list of dirs to search for include files.
     *
     * @param hash A map (String->String) of token keys to replacement
     * values. Must not be <code>null</code>.
     */
    private void setSearchdirs(final ArrayList dirs) {
        this.dirs = dirs;
    }

    
    /**
     * Returns the list of searchdirs
     *
     * @return a a list of dir to search for include files
     */
    private ArrayList getSearchdirs() {
        return dirs;
    }
  
    
  
    /**
     * Initializes setttings and searchdirs
     */
    private void initialize() {
        Parameter[] params = getParameters();
        if (params != null) {
            for (int i = 0; i < params.length; i++) {
                if (params[i] != null) {
                    final String type = params[i].getType();
                    if (TYPE_SETTING.equals(type)) {
                        String name = params[i].getName();
                        String value = params[i].getValue();
                        addSetting(name, value);
                    } else if (TYPE_SEARCHDIR.equals(type)) {
                        String value = params[i].getValue();
                        addSearchdir(value);
                    } else if (TYPE_PROPERTIESFILE.equals(type)) {
                        Properties props = getPropertiesFromFile(params[i].getValue());
                        for (Enumeration e = props.keys(); e.hasMoreElements();) {
                            String name = (String) e.nextElement();
                            String value = props.getProperty(name);
                            if (name.trim().equals(TYPE_SEARCHDIR))
                            	addSearchdir(value);
                            else
                            	addSetting(name, value);
                        }
                    }
                }
            }
        }
	}
	
	  
    /**
     * Holds a searchdir
     */
    public static class Searchdir {

        /** value */
        private String value;

        /**
         * Sets the token value
         *
         * @param value The value for this token. Must not be <code>null</code>.
         */
        public final void setValue(String value) {
            this.value = value;
        }

        /**
         * Returns the value for this token.
         *
         * @return the value for this token
         */
        public final String getValue() {
            return value;
        }
    }
	
    
    /**
     * Holds a token
     */
    public static class Setting {

        /** Setting key */
        private String key;

        /** Setting value */
        private String value;

        /**
         * Sets the setting key
         *
         * @param key The key for this setting. Must not be <code>null</code>.
         */
        public final void setKey(String key) {
            this.key = key;
        }

        /**
         * Sets the setting value
         *
         * @param value The value for this setting. Must not be <code>null</code>.
         */
        public final void setValue(String value) {
            this.value = value;
        }

        /**
         * Returns the key for this setting.
         *
         * @return the key for this setting
         */
        public final String getKey() {
            return key;
        }

        /**
         * Returns the value for this setting.
         *
         * @return the value for this setting
         */
        public final String getValue() {
            return value;
        }
    }
    
}

