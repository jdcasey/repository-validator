package org.commonjava.redhat.maven.rv.report;

import static org.apache.commons.io.IOUtils.closeQuietly;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

import javax.inject.Named;

import org.commonjava.redhat.maven.rv.ValidationException;
import org.commonjava.redhat.maven.rv.session.ValidatorSession;

@Named( "low-level-errors.log" )
public class LowLevelErrorReport
    implements ValidationReport
{

    public void write( final ValidatorSession session )
        throws IOException, ValidationException
    {
        PrintWriter writer = null;
        try
        {
            writer = session.getReportWriter( this );
            final List<Exception> lowLevelErrors = session.getLowLevelErrors();
            for ( int i = 0; i < lowLevelErrors.size(); i++ )
            {
                writer.printf( "\n%d: ", i );
                lowLevelErrors.get( i )
                              .printStackTrace( writer );
                writer.println();
            }
        }
        finally
        {
            closeQuietly( writer );
        }
    }

}
