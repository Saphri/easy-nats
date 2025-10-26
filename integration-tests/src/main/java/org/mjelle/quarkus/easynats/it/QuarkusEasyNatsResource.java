/*
* Licensed to the Apache Software Foundation (ASF) under one or more
* contributor license agreements.  See the NOTICE file distributed with
* this work for additional information regarding copyright ownership.
* The ASF licenses this file to You under the Apache License, Version 2.0
* (the "License"); you may not use this file except in compliance with
* the License.  You may obtain a copy of the License at
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package org.mjelle.quarkus.easynats.it;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import org.mjelle.quarkus.easynats.NatsPublisher;

/**
 * REST endpoints for basic EasyNATS functionality.
 * Returns proper HTTP status codes: 200 OK, 204 No Content, 400 Bad Request, 500 Internal Server Error.
 */
@Path("/quarkus-easy-nats")
public class QuarkusEasyNatsResource {

    private final NatsPublisher publisher;

    QuarkusEasyNatsResource(NatsPublisher publisher) {
        this.publisher = publisher;
    }

    /**
     * Health check endpoint.
     *
     * @return 200 OK with status message
     */
    @GET
    public Response hello() {
        return Response.ok("Hello quarkus-easy-nats").build();
    }

    /**
     * Publish a message to NATS.
     * Returns 204 No Content on success, 400 on null message, 500 on error.
     *
     * @param message the message to publish
     * @return 204 No Content if successful, 400 if message is null, 500 if error
     */
    @POST
    @Path("/publish")
    public Response publish(String message) {
        try {
            if (message == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Message cannot be null")
                    .build();
            }
            publisher.publish("test.quarkus_easy_nats", message);
            return Response.noContent().build();
        } catch (Exception e) {
            return Response.serverError().entity(e.getMessage()).build();
        }
    }
}
