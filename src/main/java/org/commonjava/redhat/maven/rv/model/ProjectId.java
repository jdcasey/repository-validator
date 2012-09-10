package org.commonjava.redhat.maven.rv.model;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Extension;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.ReportPlugin;

public class ProjectId
{

    private final String groupId;

    private final String artifactId;

    private String version;

    public ProjectId( final Plugin ref )
    {
        this.groupId = ref.getGroupId();
        this.artifactId = ref.getArtifactId();
        this.version = ref.getVersion();
    }

    public ProjectId( final ReportPlugin ref )
    {
        this.groupId = ref.getGroupId();
        this.artifactId = ref.getArtifactId();
        this.version = ref.getVersion();
    }

    public ProjectId( final Extension ref )
    {
        this.groupId = ref.getGroupId();
        this.artifactId = ref.getArtifactId();
        this.version = ref.getVersion();
    }

    public ProjectId( final Dependency ref )
    {
        this.groupId = ref.getGroupId();
        this.artifactId = ref.getArtifactId();
        this.version = ref.getVersion();
    }

    public boolean isComplete()
    {
        return version != null;
    }

    public void setVersion( final String version )
    {
        this.version = version;
    }

    public final String getGroupId()
    {
        return groupId;
    }

    public final String getArtifactId()
    {
        return artifactId;
    }

    public final String getVersion()
    {
        return version;
    }

    @Override
    public int hashCode()
    {
        final int prime = 31;
        int result = 1;
        result = prime * result + ( ( artifactId == null ) ? 0 : artifactId.hashCode() );
        result = prime * result + ( ( groupId == null ) ? 0 : groupId.hashCode() );
        result = prime * result + ( ( version == null ) ? 0 : version.hashCode() );
        return result;
    }

    @Override
    public boolean equals( final Object obj )
    {
        if ( this == obj )
        {
            return true;
        }
        if ( obj == null )
        {
            return false;
        }
        if ( getClass() != obj.getClass() )
        {
            return false;
        }
        final ProjectId other = (ProjectId) obj;
        if ( artifactId == null )
        {
            if ( other.artifactId != null )
            {
                return false;
            }
        }
        else if ( !artifactId.equals( other.artifactId ) )
        {
            return false;
        }
        if ( groupId == null )
        {
            if ( other.groupId != null )
            {
                return false;
            }
        }
        else if ( !groupId.equals( other.groupId ) )
        {
            return false;
        }
        if ( version == null )
        {
            if ( other.version != null )
            {
                return false;
            }
        }
        else if ( !version.equals( other.version ) )
        {
            return false;
        }
        return true;
    }

}
