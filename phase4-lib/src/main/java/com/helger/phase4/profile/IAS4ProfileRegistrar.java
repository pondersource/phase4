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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Base interface for AS4 profile registrar
 *
 * @author Philip Helger
 */
public interface IAS4ProfileRegistrar
{
  /**
   * Register a new AS4 profile.
   *
   * @param aAS4Profile
   *        The AS4 profile to be registered. May not be <code>null</code>.
   */
  void registerProfile (@Nonnull IAS4Profile aAS4Profile);

  /**
   * Set the provided AS4 profile as the default
   *
   * @param aAS4Profile
   *        The default AS4 profile to be used. May be <code>null</code>.
   */
  void setDefaultProfile (@Nullable IAS4Profile aAS4Profile);
}
