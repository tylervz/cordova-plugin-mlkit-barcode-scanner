/*
 * Copyright 2020 Google LLC. All rights reserved.
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

package com.mobisys.cordova.plugins.mlkit.barcode.scanner.utils;

/** Describing a frame info. */
public final class FrameMetadata {

  private final int width;
  private final int height;
  private final int rotation;

  /**
   * @return the width of the frame.
   */
  public int getWidth() {
    return width;
  }

  /**
   * @return the height of the frame.
   */
  public int getHeight() {
    return height;
  }

  /**
   * @return the rotation of the frame in degrees.
   */
  public int getRotation() {
    return rotation;
  }

  private FrameMetadata(int desiredWidth, int desiredHeight, int desiredRotation) {
    width = desiredWidth;
    height = desiredHeight;
    rotation = desiredRotation;
  }

  /** Builder for the FrameMetadata class. */
  public static class Builder {

    private int width;
    private int height;
    private int rotation;

    /**
     * Sets the FrameMetadata width.
     * @param desiredWidth desired value for the FrameMetadata width
     * @return the Builder instance
     */
    public Builder setWidth(int desiredWidth) {
      width = desiredWidth;
      return this;
    }

    /**
     * Sets the FrameMetadata height.
     * @param desiredHeight desired value for the FrameMetadata height
     * @return the Builder instance
     */
    public Builder setHeight(int desiredHeight) {
      height = desiredHeight;
      return this;
    }

    /**
     * Sets the FrameMetadata rotation.
     * @param desiredRotation desired value for the FrameMetadata rotation in degrees
     * @return the Builder instance
     */
    public Builder setRotation(int desiredRotation) {
      rotation = desiredRotation;
      return this;
    }

    /**
     * Build and return a new FrameMetadata.
     * @return the new FrameMetadata.
     */
    public FrameMetadata build() {
      return new FrameMetadata(width, height, rotation);
    }
  }
}
