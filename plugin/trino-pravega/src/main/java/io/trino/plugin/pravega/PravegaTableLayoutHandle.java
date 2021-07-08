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

package io.trino.plugin.pravega;

import io.trino.spi.connector.ConnectorTableLayoutHandle;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

import static com.google.common.base.MoreObjects.toStringHelper;
import static java.util.Objects.requireNonNull;

public class PravegaTableLayoutHandle
        implements ConnectorTableLayoutHandle
{
    private final PravegaTableHandle table;

    @JsonCreator
    public PravegaTableLayoutHandle(
            @JsonProperty("table") PravegaTableHandle table)
    {
        this.table = requireNonNull(table, "table is null");
    }

    @JsonProperty
    public PravegaTableHandle getTable()
    {
        return table;
    }

    @Override
    public String toString()
    {
        return toStringHelper(this)
                .add("table", table.toString())
                .toString();
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
        PravegaTableLayoutHandle that = (PravegaTableLayoutHandle) o;
        return Objects.equals(this.table, that.table);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(table);
    }
}
