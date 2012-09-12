package org.commonjava.redhat.maven.rv.session;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

import org.apache.log4j.Level;
import org.apache.maven.graph.common.ref.ProjectVersionRef;
import org.apache.maven.graph.common.version.InvalidVersionSpecificationException;
import org.commonjava.util.logging.Log4jUtil;
import org.junit.BeforeClass;
import org.junit.Test;

public class ValidatorSessionTest
{

    @BeforeClass
    public static void setupLogging()
    {
        Log4jUtil.configure( Level.DEBUG );
    }

    @Test
    public void addMissingThenVerifyMissingAndSeenAreTrue()
        throws InvalidVersionSpecificationException
    {
        final ValidatorSession session = new ValidatorSession.Builder( null, null ).build();
        session.addMissing( new ProjectVersionRef( "isorelax", "isorelax", "20050331" ) );

        final ProjectVersionRef ref = new ProjectVersionRef( "isorelax", "isorelax", "20050331" );
        assertThat( session.isMissing( ref ), equalTo( true ) );
    }

}
