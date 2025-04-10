/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package software.amazon.s3.analyticsaccelerator.io.logical.parquet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static software.amazon.s3.analyticsaccelerator.util.Constants.ONE_KB;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.stream.Stream;
import org.apache.parquet.format.FileMetaData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.s3.analyticsaccelerator.util.S3URI;

public class ParquetParserTest {
  private static final S3URI TEST_URI = S3URI.of("foo", "bar");

  @Test
  void testContructor() {
    assertNotNull(new ParquetParser());
  }

  @ParameterizedTest
  @MethodSource("singleRowGroupArguments")
  void testParquetMetadataParsing(String parquetFilePath, int expectedColumns) throws IOException {

    File file = new File(parquetFilePath);
    try (InputStream inputStream = new FileInputStream(file)) {

      byte[] buffer = new byte[ONE_KB * 20];
      int bytesRead = inputStream.read(buffer, 0, (int) file.length());
      assertEquals((int) file.length(), bytesRead);

      ParquetParser parquetParser = new ParquetParser();
      FileMetaData fileMetaData =
          parquetParser.parseParquetFooter(ByteBuffer.wrap(buffer), (int) file.length(), TEST_URI);

      assertEquals(fileMetaData.row_groups.size(), 1);
      assertEquals(fileMetaData.getRow_groups().get(0).getColumns().size(), expectedColumns);
    }
  }

  private static Stream<Arguments> singleRowGroupArguments() {
    return Stream.of(
        Arguments.of("src/test/resources/call_center.parquet", 31),
        Arguments.of("src/test/resources/nested_data.parquet", 8));
  }

  private static Stream<Arguments> multiRowGroupArguments() {
    return Stream.of(
        Arguments.of("src/test/resources/multi_row_group.parquet", 3, 2),
        Arguments.of("src/test/resources/nested_data_mrg.parquet", 3, 8));
  }

  @ParameterizedTest
  @MethodSource("multiRowGroupArguments")
  void testParquetMetadataParsingMultipleRowGroups(
      String fileName, int expectedRowGroups, int expectedColumns) throws IOException {

    File file = new File(fileName);
    try (InputStream inputStream = new FileInputStream(file)) {

      byte[] buffer = new byte[ONE_KB * 20];
      int bytesRead = inputStream.read(buffer, 0, (int) file.length());
      assertEquals((int) file.length(), bytesRead);

      ParquetParser parquetParser = new ParquetParser();
      FileMetaData fileMetaData =
          parquetParser.parseParquetFooter(ByteBuffer.wrap(buffer), (int) file.length(), TEST_URI);

      assertEquals(fileMetaData.row_groups.size(), expectedRowGroups);
      assertEquals(fileMetaData.getRow_groups().get(0).getColumns().size(), expectedColumns);
    }
  }

  @Test
  void testParquetMetadataParsingInvalidData() {

    ParquetParser parquetParserInvalidLength = new ParquetParser();
    assertThrows(
        IllegalArgumentException.class,
        () -> {
          parquetParserInvalidLength.parseParquetFooter(ByteBuffer.allocate(ONE_KB), 8, TEST_URI);
        });

    // Empty buffer, will throw thrift exception
    ParquetParser parquetParserInvalidBuffer = new ParquetParser();
    assertThrows(
        IOException.class,
        () -> {
          parquetParserInvalidBuffer.parseParquetFooter(ByteBuffer.allocate(ONE_KB), 9, TEST_URI);
        });
  }

  @Test
  void testInvalidFileTail() throws IOException {
    File file = new File("src/test/resources/call_center.parquet");

    try (InputStream inputStream = new FileInputStream(file)) {
      byte[] buffer = new byte[ONE_KB * 20];
      int bytesRead = inputStream.read(buffer, 0, (int) file.length());
      assertEquals((int) file.length(), bytesRead);

      byte[] tailBuffer = new byte[20];
      int j = (int) file.length() - 1;
      for (int i = 19; i >= 0; i--) {
        tailBuffer[i] = buffer[j];
        j--;
      }

      // Test the case where the tail buffer is < the size of the footer metadata.
      // In this case we want to throw an IOException
      ParquetParser parquetParser = new ParquetParser();
      assertThrows(
          IOException.class,
          () -> parquetParser.parseParquetFooter(ByteBuffer.wrap(tailBuffer), 20, TEST_URI));
    }
  }
}
