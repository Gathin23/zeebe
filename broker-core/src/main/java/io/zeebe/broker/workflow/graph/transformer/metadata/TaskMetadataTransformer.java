/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.workflow.graph.transformer.metadata;

import static io.zeebe.broker.workflow.graph.transformer.ZeebeExtensions.*;
import static io.zeebe.util.EnsureUtil.ensureNotNull;

import java.util.List;

import io.zeebe.broker.workflow.graph.model.metadata.TaskMetadata;
import io.zeebe.msgpack.spec.MsgPackWriter;
import io.zeebe.util.buffer.BufferUtil;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.bpm.model.bpmn.instance.ExtensionElements;
import org.camunda.bpm.model.xml.instance.DomElement;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;

public class TaskMetadataTransformer
{
    private static final int DEFAULT_TASK_RETRIES = 3;
    public static final int INITIAL_SIZE_KEY_VALUE_PAIR = 256;

    public static TaskMetadata transform(ExtensionElements extensionElements)
    {
        final TaskMetadata metadata = new TaskMetadata();

        // TODO #202 - provide Zeebe model instance
        final ModelElementInstance taskDefinition = getTaskDefinition(extensionElements);

        final String type = getTaskType(taskDefinition);
        metadata.setTaskType(BufferUtil.wrapString(type));

        final int retries = getTaskRetries(taskDefinition);
        metadata.setRetries(retries);

        final MutableDirectBuffer taskHeaders = getTaskHeaders(extensionElements);
        metadata.setHeaders(taskHeaders);

        return metadata;
    }

    private static ModelElementInstance getTaskDefinition(ExtensionElements extensionElements)
    {
        final ModelElementInstance taskDefinition = extensionElements.getUniqueChildElementByNameNs(ZEEBE_NAMESPACE, TASK_DEFINITION_ELEMENT);
        ensureNotNull("task definition", taskDefinition);
        return taskDefinition;
    }

    private static String getTaskType(final ModelElementInstance taskDefinition)
    {
        final String type = taskDefinition.getAttributeValue(TASK_TYPE_ATTRIBUTE);
        ensureNotNull("task type", type);
        return type;
    }

    private static int getTaskRetries(final ModelElementInstance taskDefinition)
    {
        int retries = DEFAULT_TASK_RETRIES;

        final String configuredRetries = taskDefinition.getAttributeValue(TASK_RETRIES_ATTRIBUTE);
        if (configuredRetries != null && !configuredRetries.isEmpty())
        {
            try
            {
                retries = Integer.parseInt(configuredRetries);
            }
            catch (NumberFormatException e)
            {
                throw new RuntimeException("Failed to parse task retries. Expected number but found: " + configuredRetries);
            }
        }

        return retries;
    }

    private static MutableDirectBuffer getTaskHeaders(ExtensionElements extensionElements)
    {
        final MutableDirectBuffer buffer = new UnsafeBuffer(0, 0);

        final ModelElementInstance taskHeadersElement = extensionElements.getUniqueChildElementByNameNs(ZEEBE_NAMESPACE, TASK_HEADERS_ELEMENT);
        if (taskHeadersElement != null)
        {
            final List<DomElement> headerElements = taskHeadersElement.getDomElement().getChildElementsByNameNs(ZEEBE_NAMESPACE, TASK_HEADER_ELEMENT);

            final MsgPackWriter msgPackWriter = new MsgPackWriter();
            final ExpandableArrayBuffer expandableBuffer = new ExpandableArrayBuffer(INITIAL_SIZE_KEY_VALUE_PAIR * headerElements.size());
            msgPackWriter.wrap(expandableBuffer, 0);
            msgPackWriter.writeMapHeader(headerElements.size());

            for (int i = 0; i < headerElements.size(); i++)
            {
                final DomElement header = headerElements.get(i);

                final String key = header.getAttribute(TASK_HEADER_KEY_ATTRIBUTE);
                msgPackWriter.writeString(BufferUtil.wrapString(key));

                final String value = header.getAttribute(TASK_HEADER_VALUE_ATTRIBUTE);
                msgPackWriter.writeString(BufferUtil.wrapString(value));
            }

            buffer.wrap(expandableBuffer.byteArray(), 0, msgPackWriter.getOffset());
        }
        return buffer;
    }
}
