package org.apache.maven.plugin.assembly.filter;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import org.apache.maven.plugin.assembly.testutils.TestFileManager;
import org.codehaus.plexus.archiver.ArchivedFileSet;
import org.codehaus.plexus.archiver.Archiver;
import org.codehaus.plexus.archiver.ArchiverException;
import org.codehaus.plexus.archiver.FileSet;
import org.codehaus.plexus.archiver.ResourceIterator;
import org.codehaus.plexus.archiver.zip.ZipArchiver;
import org.codehaus.plexus.components.io.resources.PlexusIoResource;
import org.codehaus.plexus.components.io.resources.PlexusIoResourceCollection;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.xml.PrettyPrintXMLWriter;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.jdom.Document;
import org.jdom.JDOMException;
import org.jdom.Text;
import org.jdom.input.SAXBuilder;
import org.jdom.xpath.XPath;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import junit.framework.TestCase;

public class ComponentsXmlArchiverFileFilterTest
    extends TestCase
{
    private ComponentsXmlArchiverFileFilter filter;

    private TestFileManager fileManager = new TestFileManager( "componentsXmlArchiverFileFilter.test", ".zip" );

    public void setUp()
    {
        filter = new ComponentsXmlArchiverFileFilter();
    }

    public void tearDown()
        throws IOException
    {
        fileManager.cleanUp();
    }

    public void testAddComponentsXml_ShouldAddComponentWithoutRoleHint()
        throws IOException, XmlPullParserException
    {
        Reader reader = writeComponentsXml( Collections.singletonList( new ComponentDef( "role", null,
                                                                                         "org.apache.maven.Impl" ) ) );

        filter.addComponentsXml( reader );

        assertFalse( filter.components.isEmpty() );

        Xpp3Dom componentDom = (Xpp3Dom) filter.components.get( "role" );

        assertEquals( "role", componentDom.getChild( "role" ).getValue() );
        assertNull( componentDom.getChild( "role-hint" ) );
        assertEquals( "org.apache.maven.Impl", componentDom.getChild( "implementation" ).getValue() );
    }

    public void testAddComponentsXml_ShouldAddComponentWithRoleHint()
        throws IOException, XmlPullParserException
    {
        Reader reader = writeComponentsXml( Collections.singletonList( new ComponentDef( "role", "hint",
                                                                                         "org.apache.maven.Impl" ) ) );

        filter.addComponentsXml( reader );

        assertFalse( filter.components.isEmpty() );

        Xpp3Dom componentDom = (Xpp3Dom) filter.components.get( "rolehint" );

        assertEquals( "role", componentDom.getChild( "role" ).getValue() );
        assertEquals( "hint", componentDom.getChild( "role-hint" ).getValue() );
        assertEquals( "org.apache.maven.Impl", componentDom.getChild( "implementation" ).getValue() );
    }

    public void testAddComponentsXml_ShouldAddTwoComponentsWithRoleHints()
        throws IOException, XmlPullParserException
    {
        List defs = new ArrayList();

        defs.add( new ComponentDef( "role", "hint", "org.apache.maven.Impl" ) );
        defs.add( new ComponentDef( "role", "hint2", "org.apache.maven.Impl2" ) );

        Reader reader = writeComponentsXml( defs );

        filter.addComponentsXml( reader );

        assertFalse( filter.components.isEmpty() );

        Xpp3Dom componentDom = (Xpp3Dom) filter.components.get( "rolehint" );

        assertEquals( "role", componentDom.getChild( "role" ).getValue() );
        assertEquals( "hint", componentDom.getChild( "role-hint" ).getValue() );
        assertEquals( "org.apache.maven.Impl", componentDom.getChild( "implementation" ).getValue() );

        componentDom = (Xpp3Dom) filter.components.get( "rolehint2" );

        assertEquals( "role", componentDom.getChild( "role" ).getValue() );
        assertEquals( "hint2", componentDom.getChild( "role-hint" ).getValue() );
        assertEquals( "org.apache.maven.Impl2", componentDom.getChild( "implementation" ).getValue() );
    }

    public void testAddToArchive_ShouldWriteComponentWithoutHintToFile()
        throws IOException, ArchiverException, JDOMException
    {
        Xpp3Dom dom = createComponentDom( new ComponentDef( "role", null, "impl" ) );

        filter.components = new LinkedHashMap();
        filter.components.put( "role", dom );

        FileCatchingArchiver fca = new FileCatchingArchiver();

        filter.finalizeArchiveCreation( fca );

        assertEquals( ComponentsXmlArchiverFileFilter.COMPONENTS_XML_PATH, fca.getDestFileName() );

        SAXBuilder builder = new SAXBuilder( false );

        Document doc = builder.build( fca.getFile() );

        XPath role = XPath.newInstance( "//component[position()=1]/role/text()" );
        XPath hint = XPath.newInstance( "//component[position()=1]/role-hint/text()" );
        XPath implementation = XPath.newInstance( "//component[position()=1]/implementation/text()" );

        assertEquals( "role", ( (Text) role.selectSingleNode( doc ) ).getText() );
        assertNull( hint.selectSingleNode( doc ) );
        assertEquals( "impl", ( (Text) implementation.selectSingleNode( doc ) ).getText() );
    }

    public void testAddToArchive_ShouldWriteComponentWithHintToFile()
        throws IOException, ArchiverException, JDOMException
    {
        Xpp3Dom dom = createComponentDom( new ComponentDef( "role", "hint", "impl" ) );

        filter.components = new LinkedHashMap();
        filter.components.put( "rolehint", dom );

        FileCatchingArchiver fca = new FileCatchingArchiver();

        filter.finalizeArchiveCreation( fca );

        assertEquals( ComponentsXmlArchiverFileFilter.COMPONENTS_XML_PATH, fca.getDestFileName() );

        SAXBuilder builder = new SAXBuilder( false );

        Document doc = builder.build( fca.getFile() );

        XPath role = XPath.newInstance( "//component[position()=1]/role/text()" );
        XPath hint = XPath.newInstance( "//component[position()=1]/role-hint/text()" );
        XPath implementation = XPath.newInstance( "//component[position()=1]/implementation/text()" );

        assertEquals( "role", ( (Text) role.selectSingleNode( doc ) ).getText() );
        assertEquals( "hint", ( (Text) hint.selectSingleNode( doc ) ).getText() );
        assertEquals( "impl", ( (Text) implementation.selectSingleNode( doc ) ).getText() );
    }

    public void testAddToArchive_ShouldWriteTwoComponentToFile()
        throws IOException, ArchiverException, JDOMException
    {
        filter.components = new LinkedHashMap();

        Xpp3Dom dom = createComponentDom( new ComponentDef( "role", "hint", "impl" ) );

        filter.components.put( "rolehint", dom );

        Xpp3Dom dom2 = createComponentDom( new ComponentDef( "role", "hint2", "impl" ) );

        filter.components.put( "rolehint2", dom2 );

        FileCatchingArchiver fca = new FileCatchingArchiver();

        filter.finalizeArchiveCreation( fca );

        assertEquals( ComponentsXmlArchiverFileFilter.COMPONENTS_XML_PATH, fca.getDestFileName() );

        SAXBuilder builder = new SAXBuilder( false );

        Document doc = builder.build( fca.getFile() );

        XPath role = XPath.newInstance( "//component[position()=1]/role/text()" );
        XPath hint = XPath.newInstance( "//component[position()=1]/role-hint/text()" );
        XPath implementation = XPath.newInstance( "//component[position()=1]/implementation/text()" );

        assertEquals( "role", ( (Text) role.selectSingleNode( doc ) ).getText() );
        assertEquals( "hint", ( (Text) hint.selectSingleNode( doc ) ).getText() );
        assertEquals( "impl", ( (Text) implementation.selectSingleNode( doc ) ).getText() );

        XPath role2 = XPath.newInstance( "//component[position()=2]/role/text()" );
        XPath hint2 = XPath.newInstance( "//component[position()=2]/role-hint/text()" );
        XPath implementation2 = XPath.newInstance( "//component[position()=2]/implementation/text()" );

        assertEquals( "role", ( (Text) role2.selectSingleNode( doc ) ).getText() );
        assertEquals( "hint2", ( (Text) hint2.selectSingleNode( doc ) ).getText() );
        assertEquals( "impl", ( (Text) implementation2.selectSingleNode( doc ) ).getText() );

    }

    public void testAddToArchive_ShouldWriteTwoComponentToArchivedFile()
        throws IOException, ArchiverException, JDOMException
    {
        filter.components = new LinkedHashMap();

        Xpp3Dom dom = createComponentDom( new ComponentDef( "role", "hint", "impl" ) );

        filter.components.put( "rolehint", dom );

        Xpp3Dom dom2 = createComponentDom( new ComponentDef( "role", "hint2", "impl" ) );

        filter.components.put( "rolehint2", dom2 );

        ZipArchiver archiver = new ZipArchiver();

        File archiveFile = fileManager.createTempFile();

        archiver.setDestFile( archiveFile );

        File descriptorFile = fileManager.createTempFile();

        archiver.setArchiveFinalizers( Collections.singletonList( filter ) );

        archiver.createArchive();

        ZipFile zf = new ZipFile( archiveFile );

        ZipEntry ze = zf.getEntry( ComponentsXmlArchiverFileFilter.COMPONENTS_XML_PATH );

        assertNotNull( ze );

        FileOutputStream fileStream = new FileOutputStream( descriptorFile );

        IOUtil.copy( zf.getInputStream( ze ), fileStream );
        IOUtil.close( fileStream );

        SAXBuilder builder = new SAXBuilder( false );

        Document doc = builder.build( descriptorFile );

        XPath role = XPath.newInstance( "//component[position()=1]/role/text()" );
        XPath hint = XPath.newInstance( "//component[position()=1]/role-hint/text()" );
        XPath implementation = XPath.newInstance( "//component[position()=1]/implementation/text()" );

        assertEquals( "role", ( (Text) role.selectSingleNode( doc ) ).getText() );
        assertEquals( "hint", ( (Text) hint.selectSingleNode( doc ) ).getText() );
        assertEquals( "impl", ( (Text) implementation.selectSingleNode( doc ) ).getText() );

        XPath role2 = XPath.newInstance( "//component[position()=2]/role/text()" );
        XPath hint2 = XPath.newInstance( "//component[position()=2]/role-hint/text()" );
        XPath implementation2 = XPath.newInstance( "//component[position()=2]/implementation/text()" );

        assertEquals( "role", ( (Text) role2.selectSingleNode( doc ) ).getText() );
        assertEquals( "hint2", ( (Text) hint2.selectSingleNode( doc ) ).getText() );
        assertEquals( "impl", ( (Text) implementation2.selectSingleNode( doc ) ).getText() );

    }

    private Xpp3Dom createComponentDom( ComponentDef def )
    {
        Xpp3Dom dom = new Xpp3Dom( "component" );

        Xpp3Dom role = new Xpp3Dom( "role" );
        role.setValue( def.role );
        dom.addChild( role );

        String hint = def.roleHint;
        if ( hint != null )
        {
            Xpp3Dom roleHint = new Xpp3Dom( "role-hint" );
            roleHint.setValue( hint );
            dom.addChild( roleHint );
        }

        Xpp3Dom impl = new Xpp3Dom( "implementation" );
        impl.setValue( def.implementation );
        dom.addChild( impl );

        return dom;
    }

    private Reader writeComponentsXml( List componentDefs )
        throws IOException
    {
        StringWriter writer = new StringWriter();

        PrettyPrintXMLWriter xmlWriter = new PrettyPrintXMLWriter( writer );

        xmlWriter.startElement( "component-set" );
        xmlWriter.startElement( "components" );

        for ( Iterator it = componentDefs.iterator(); it.hasNext(); )
        {
            ComponentDef def = (ComponentDef) it.next();

            xmlWriter.startElement( "component" );

            xmlWriter.startElement( "role" );
            xmlWriter.writeText( def.role );
            xmlWriter.endElement();

            String roleHint = def.roleHint;
            if ( roleHint != null )
            {
                xmlWriter.startElement( "role-hint" );
                xmlWriter.writeText( roleHint );
                xmlWriter.endElement();
            }

            xmlWriter.startElement( "implementation" );
            xmlWriter.writeText( def.implementation );
            xmlWriter.endElement();

            xmlWriter.endElement();
        }

        xmlWriter.endElement();
        xmlWriter.endElement();

        return new StringReader( writer.toString() );
    }

    private static final class ComponentDef
    {
        String role;

        String roleHint;

        String implementation;

        ComponentDef( String role, String roleHint, String implementation )
        {
            this.role = role;
            this.roleHint = roleHint;
            this.implementation = implementation;

        }
    }

    private static final class FileCatchingArchiver
        implements Archiver
    {

        private File inputFile;

        private String destFileName;

        public void addDirectory( File directory )
            throws ArchiverException
        {
            throw new UnsupportedOperationException( "not supported" );
        }

        public void addDirectory( File directory, String prefix )
            throws ArchiverException
        {
            throw new UnsupportedOperationException( "not supported" );
        }

        public void addDirectory( File directory, String[] includes, String[] excludes )
            throws ArchiverException
        {
            throw new UnsupportedOperationException( "not supported" );
        }

        public void addDirectory( File directory, String prefix, String[] includes, String[] excludes )
            throws ArchiverException
        {
            throw new UnsupportedOperationException( "not supported" );
        }

        public void addFile( File inputFile, String destFileName )
            throws ArchiverException
        {
            this.inputFile = inputFile;
            this.destFileName = destFileName;
        }

        File getFile()
        {
            return inputFile;
        }

        String getDestFileName()
        {
            return destFileName;
        }

        public void addFile( File inputFile, String destFileName, int permissions )
            throws ArchiverException
        {
            throw new UnsupportedOperationException( "not supported" );
        }

        public void createArchive()
            throws ArchiverException, IOException
        {
            throw new UnsupportedOperationException( "not supported" );
        }

        public int getDefaultDirectoryMode()
        {
            throw new UnsupportedOperationException( "not supported" );
        }

        public int getDefaultFileMode()
        {
            throw new UnsupportedOperationException( "not supported" );
        }

        public File getDestFile()
        {
            throw new UnsupportedOperationException( "not supported" );
        }

        public Map getFiles()
        {
            throw new UnsupportedOperationException( "not supported" );
        }

        public boolean getIncludeEmptyDirs()
        {
            throw new UnsupportedOperationException( "not supported" );
        }

        public void setDefaultDirectoryMode( int mode )
        {
            throw new UnsupportedOperationException( "not supported" );
        }

        public void setDefaultFileMode( int mode )
        {
            throw new UnsupportedOperationException( "not supported" );
        }

        public void setDestFile( File destFile )
        {
            throw new UnsupportedOperationException( "not supported" );
        }

        public void setIncludeEmptyDirs( boolean includeEmptyDirs )
        {
            throw new UnsupportedOperationException( "not supported" );
        }

        public void addArchivedFileSet( File archiveFile )
            throws ArchiverException
        {
            throw new UnsupportedOperationException( "not supported" );
        }

        public void addArchivedFileSet( File archiveFile, String prefix )
            throws ArchiverException
        {
            throw new UnsupportedOperationException( "not supported" );
        }

        public void addArchivedFileSet( File archiveFile, String[] includes, String[] excludes )
            throws ArchiverException
        {
            throw new UnsupportedOperationException( "not supported" );
        }

        public void addArchivedFileSet( File archiveFile, String prefix, String[] includes, String[] excludes )
            throws ArchiverException
        {
            throw new UnsupportedOperationException( "not supported" );
        }

        public void setForced( boolean forced )
        {
            throw new UnsupportedOperationException( "not supported" );
        }

        public boolean isForced()
        {
            throw new UnsupportedOperationException( "not supported" );
        }

        public boolean isSupportingForced()
        {
            throw new UnsupportedOperationException( "not supported" );
        }

        public void setDotFileDirectory( File dotFileDirectory )
        {
        }

        public void addArchivedFileSet( ArchivedFileSet fileSet )
            throws ArchiverException
        {
            throw new UnsupportedOperationException( "not supported" );
        }

        public void addFileSet( FileSet fileSet )
            throws ArchiverException
        {
            throw new UnsupportedOperationException( "not supported" );
        }

        public void addResource( PlexusIoResource resource,
                                 String destFileName,
                                 int permissions )
            throws ArchiverException
        {
            throw new UnsupportedOperationException( "not supported" );
        }

        public void addResources( PlexusIoResourceCollection resources )
            throws ArchiverException
        {
            throw new UnsupportedOperationException( "not supported" );
        }

        public ResourceIterator getResources()
            throws ArchiverException
        {
            throw new UnsupportedOperationException( "not supported" );
        }
    }

}
