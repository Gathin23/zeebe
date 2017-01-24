package org.camunda.tngp.client.impl.cmd;

import org.agrona.DirectBuffer;
import org.camunda.tngp.client.cmd.BrokerRequestException;
import org.camunda.tngp.protocol.clientapi.ErrorResponseDecoder;

public class ClientErrorResponseHandler
{
    protected ErrorResponseDecoder errorResponseDecoder = new ErrorResponseDecoder();

    public Throwable createException(final DirectBuffer responseBuffer, final int offset, final int length)
    {
        errorResponseDecoder.wrap(responseBuffer, offset, errorResponseDecoder.sbeBlockLength(), errorResponseDecoder.sbeSchemaVersion());

        final short errorCode = errorResponseDecoder.errorCode().value();
        final String errorData = errorResponseDecoder.errorData();

        return new BrokerRequestException(errorCode, errorData);
    }

}
