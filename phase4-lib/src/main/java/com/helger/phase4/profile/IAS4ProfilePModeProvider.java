/*
 * Copyright (C) 2015-2022 Philip Helger (www.helger.com)
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
package com.helger.phase4.profile;

import java.io.Serializable;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.helger.commons.annotation.Nonempty;
import com.helger.phase4.model.pmode.PMode;

/**
 * PMode provider interface
 * 
 * @author Philip Helger
 */
@FunctionalInterface
public interface IAS4ProfilePModeProvider extends Serializable
{
  /**
   * Get an existing or create a new PMode.
   *
   * @param sInitiatorID
   *        The initiator ID. May neither be <code>null</code> nor empty.
   * @param sResponderID
   *        The responder ID. May neither be <code>null</code> nor empty.
   * @param sAddress
   *        The endpoint URL address. May be <code>null</code>.
   * @return The PMode matching the params or <code>null</code>.
   */
  @Nullable
  PMode getOrCreatePMode (@Nonnull @Nonempty String sInitiatorID,
                          @Nonnull @Nonempty String sResponderID,
                          @Nullable String sAddress);
}
