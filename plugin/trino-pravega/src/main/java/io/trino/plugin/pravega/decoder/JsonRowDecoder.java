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

package io.trino.plugin.pravega.decoder;

import io.trino.plugin.pravega.PravegaRecordValue;
// import io.trino.plugin.pravega.TypedRecordValue;

public class JsonRowDecoder
        implements EventDecoder
{
    private final JsonRowDecoder jsonRowDecoder;

    public JsonRowDecoder(JsonRowDecoder jsonRowDecoder)
    {
        this.jsonRowDecoder = jsonRowDecoder;
    }

    @Override
    public boolean decodeEvent(DecodableEvent event, PravegaRecordValue record)
    {
        // ((TypedRecordValue) record).setDecodedValue(jsonRowDecoder.decodeTree(event.asJson()));
        return true;
    }
}
