/*
 * Copyright (C) 2021-2022 Philip Helger (www.helger.com)
 * philip[at]helger[dot]com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.helger.phase4.springboot.service;

import javax.annotation.Nonnull;

import org.springframework.stereotype.Service;
import org.unece.cefact.namespaces.sbdh.StandardBusinessDocument;

import com.helger.commons.http.HttpHeaderMap;
import com.helger.peppol.sbdh.PeppolSBDHDocument;
import com.helger.phase4.ebms3header.Ebms3UserMessage;
import com.helger.phase4.messaging.IAS4IncomingMessageMetadata;
import com.helger.phase4.servlet.IAS4MessageState;

@Service
public interface ISBDHandlerService
{
  void setMessageMetadata (@Nonnull IAS4IncomingMessageMetadata messageMetadata);

  void setHttpHeaders (@Nonnull HttpHeaderMap httpHeaders);

  void setUserMessage (@Nonnull Ebms3UserMessage userMessage);

  void setStandardBusinessDocument (@Nonnull StandardBusinessDocument standardBusinessDocument);

  void setStandardBusinessDocumentBytes (@Nonnull byte [] standardBusinessDocumentBytes);

  void setPeppolStandardBusinessDocumentHeader (@Nonnull PeppolSBDHDocument peppolStandardBusinessDocumentHeader);

  void setMessageState (@Nonnull IAS4MessageState messageState);

  void handle () throws Exception;
}
