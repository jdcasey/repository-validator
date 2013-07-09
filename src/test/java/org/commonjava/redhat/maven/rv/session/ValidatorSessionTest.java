package org.commonjava.redhat.maven.rv.session;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

import java.io.File;

import org.apache.log4j.Level;
import org.apache.maven.graph.common.ref.ProjectVersionRef;
import org.apache.maven.graph.common.version.InvalidVersionSpecificationException;
import org.commonjava.util.logging.Log4jUtil;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ValidatorSessionTest
{

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @BeforeClass
    public static void setupLogging()
    {
        Log4jUtil.configure( Level.DEBUG );
    }

    @Test
    public void addMissingThenVerifyMissingAndSeenAreTrue()
        throws InvalidVersionSpecificationException, Exception
    {
        final File repo = temp.newFolder();
        final File ws = temp.newFolder();

        final ValidatorSession session = new ValidatorSession.Builder( repo, ws ).build();
        session.addMissing( new ProjectVersionRef( "isorelax", "isorelax", "20050331" ) );

        final ProjectVersionRef ref = new ProjectVersionRef( "isorelax", "isorelax", "20050331" );
        assertThat( session.isMissing( ref ), equalTo( true ) );
    }

    @Test
    public void addIdenticalRefsToMissingThenVerifyOnlyOneAdded()
        throws InvalidVersionSpecificationException, Exception
    {
        final File repo = temp.newFolder();
        final File ws = temp.newFolder();

        final ValidatorSession session = new ValidatorSession.Builder( repo, ws ).build();
        session.addMissing( new ProjectVersionRef( "isorelax", "isorelax", "20050331" ) );

        final ProjectVersionRef ref = new ProjectVersionRef( "isorelax", "isorelax", "20050331" );
        assertThat( session.isMissing( ref ), equalTo( true ) );

        session.addMissing( ref );
        assertThat( session.getMissing()
                           .size(), equalTo( 1 ) );
        assertThat( session.getSeen()
                           .size(), equalTo( 1 ) );
    }

}
