/*
 * Copyright (c) Pravega Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.trino.plugin.s3;

import io.trino.spi.predicate.TupleDomain;
import io.trino.spi.connector.ColumnHandle;
import io.trino.spi.connector.ConnectorTableLayoutHandle;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

public class S3TableLayoutHandle
        implements ConnectorTableLayoutHandle
{
    private final S3TableHandle table;

    private final TupleDomain<ColumnHandle> constraints;

    @JsonCreator
    public S3TableLayoutHandle(@JsonProperty("table") S3TableHandle table,
            @JsonProperty("constraints") TupleDomain<ColumnHandle> constraints)
    {
        this.table = table;
        this.constraints = constraints;
    }

    @JsonProperty
    public S3TableHandle getTable()
    {
        return table;
    }

    @JsonProperty
    public TupleDomain<ColumnHandle> getConstraints()
    {
        return constraints;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        S3TableLayoutHandle that = (S3TableLayoutHandle) o;
        return Objects.equals(table, that.table);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(table);
    }

    @Override
    public String toString()
    {
        return table.toString();
    }
}
