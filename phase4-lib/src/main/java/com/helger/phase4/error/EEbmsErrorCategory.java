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
package com.helger.phase4.error;

import javax.annotation.Nonnull;

import com.helger.commons.annotation.Nonempty;
import com.helger.commons.name.IHasDisplayName;

/**
 * EBMS error category enumeration
 *
 * @author Philip Helger
 */
public enum EEbmsErrorCategory implements IHasDisplayName
{
  CONTENT ("Content"),
  COMMUNICATION ("Communication"),
  UNPACKAGING ("Unpackaging"),
  PROCESSING ("Processing");

  private final String m_sContent;

  EEbmsErrorCategory (@Nonnull @Nonempty final String sContent)
  {
    m_sContent = sContent;
  }

  @Nonnull
  @Nonempty
  public String getDisplayName ()
  {
    return m_sContent;
  }
}
