package org.commonjava.redhat.maven.rv.report;

import java.io.IOException;

import org.commonjava.redhat.maven.rv.ValidationException;
import org.commonjava.redhat.maven.rv.session.ValidatorSession;

public interface ValidationReport
{

    boolean canRun( ValidatorSession session );

    void write( ValidatorSession session )
        throws IOException, ValidationException;

}
