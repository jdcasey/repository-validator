package org.commonjava.redhat.maven.rv.report;

import static org.apache.commons.io.IOUtils.closeQuietly;
import static org.apache.commons.lang.StringUtils.join;

import java.io.IOException;
import java.io.PrintWriter;

import javax.inject.Named;

import org.commonjava.redhat.maven.rv.ValidationException;
import org.commonjava.redhat.maven.rv.session.ValidatorSession;

@Named( "boms.txt" )
public class BomsReport
    implements ValidationReport
{

    public void write( final ValidatorSession session )
        throws IOException, ValidationException
    {
        PrintWriter writer = null;
        try
        {
            writer = session.getReportWriter( this );

            writer.println( join( session.getBoms(), "\n" ) );
        }
        finally
        {
            closeQuietly( writer );
        }
    }

}
