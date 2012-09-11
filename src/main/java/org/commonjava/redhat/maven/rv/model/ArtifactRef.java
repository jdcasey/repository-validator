package org.commonjava.redhat.maven.rv.model;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Extension;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.ReportPlugin;

public class ArtifactRef
{

    private final String groupId;

    private final String artifactId;

    private String version;

    private String type;

    public ArtifactRef( final ArtifactRef aid, final String type )
    {
        this.groupId = aid.getGroupId();
        this.artifactId = aid.getArtifactId();
        this.version = aid.getVersion();
        this.type = type;
    }

    public ArtifactRef( final Plugin ref )
    {
        this.groupId = ref.getGroupId();
        this.artifactId = ref.getArtifactId();
        this.version = ref.getVersion();
        this.type = "maven-plugin";
    }

    public ArtifactRef( final ReportPlugin ref )
    {
        this.groupId = ref.getGroupId();
        this.artifactId = ref.getArtifactId();
        this.version = ref.getVersion();
        this.type = "maven-plugin";
    }

    public ArtifactRef( final Extension ref )
    {
        this.groupId = ref.getGroupId();
        this.artifactId = ref.getArtifactId();
        this.version = ref.getVersion();
        this.type = "jar";
    }

    public ArtifactRef( final Dependency ref )
    {
        this.groupId = ref.getGroupId();
        this.artifactId = ref.getArtifactId();
        this.version = ref.getVersion();
        this.type = ( ref.getType() == null ? "jar" : ref.getType() );
    }

    public ArtifactRef( final Model model )
    {
        this.groupId = model.getGroupId();
        this.artifactId = model.getArtifactId();
        this.version = model.getVersion();
        this.type = "pom";
    }

    public boolean isComplete()
    {
        return version != null;
    }

    public void setVersion( final String version )
    {
        this.version = version;
    }

    public final String getType()
    {
        return type;
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
        result = prime * result + ( ( type == null ) ? 0 : type.hashCode() );
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
        final ArtifactRef other = (ArtifactRef) obj;
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
        if ( type == null )
        {
            if ( other.type != null )
            {
                return false;
            }
        }
        else if ( !type.equals( other.type ) )
        {
            return false;
        }
        return true;
    }

    @Override
    public String toString()
    {
        return String.format( "%s:%s:%s:%s", groupId, artifactId, ( version == null ? "-UNKNOWN-" : version ), type );
    }

}
