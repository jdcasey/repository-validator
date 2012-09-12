package org.commonjava.redhat.maven.rv.util;

import java.util.Comparator;

public class ToStringComparator<T>
    implements Comparator<T>
{

    public int compare( final T first, final T second )
    {
        return first.toString()
                    .compareTo( second.toString() );
    }

}
