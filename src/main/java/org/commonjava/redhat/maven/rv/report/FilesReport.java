package org.commonjava.redhat.maven.rv.report;

import static org.apache.commons.io.IOUtils.closeQuietly;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Named;

import org.apache.maven.graph.common.ref.ProjectVersionRef;
import org.commonjava.redhat.maven.rv.ValidationException;
import org.commonjava.redhat.maven.rv.session.ValidatorSession;

@Named( "files.log" )
public class FilesReport
    implements ValidationReport
{

    public void write( final ValidatorSession session )
        throws IOException, ValidationException
    {
        PrintWriter writer = null;
        try
        {
            writer = session.getReportWriter( this );
            final Map<ProjectVersionRef, Set<String>> allProjectFiles = session.getAllProjectFiles();
            for ( final Map.Entry<ProjectVersionRef, Set<String>> entry : allProjectFiles.entrySet() )
            {
                writer.printf( "%s:\n----------------------------------------\n", entry.getKey() );
                final Set<String> files = entry.getValue();
                if ( files == null || files.isEmpty() )
                {
                    writer.printf( "\n  -None-" );
                }
                else
                {
                    final List<String> f = new ArrayList<String>( files );
                    Collections.sort( f );

                    for ( final String file : f )
                    {
                        writer.printf( "\n  %s", file );
                    }
                }

                writer.println();
                writer.println();
            }
        }
        finally
        {
            closeQuietly( writer );
        }
    }

}
