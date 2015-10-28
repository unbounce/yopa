<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:param name="region" />
  <xsl:param name="sqs-port" />
  <xsl:param name="sqs-host" />
  <xsl:param name="sqs-https" />
  <xsl:param name="sns-port" />
  <xsl:param name="sns-host" />
  <xsl:param name="sns-https" />
  <xsl:param name="s3-port" />
  <xsl:param name="s3-host" />
  <xsl:param name="s3-https" />
  <xsl:output method="xml" indent="yes" />

  <xsl:template match="@*|node()">
    <xsl:copy>
      <xsl:apply-templates select="@*|node()"/>
    </xsl:copy>
  </xsl:template>

  <xsl:template match="Regions">
    <xsl:copy>
      <Region>
        <Name>
          <xsl:value-of select="$region" />
        </Name>
        <Endpoint>
          <ServiceName>sqs</ServiceName>
          <Http>true</Http>
          <Https><xsl:value-of select="$sqs-https" /></Https>
          <Hostname>
            <xsl:value-of select="$sqs-host" />
          </Hostname>
        </Endpoint>
        <Endpoint>
          <ServiceName>sns</ServiceName>
          <Http>true</Http>
          <Https><xsl:value-of select="$sns-https" /></Https>
          <Hostname>
            <xsl:value-of select="$sns-host" />
          </Hostname>
        </Endpoint>
        <Endpoint>
          <ServiceName>s3</ServiceName>
          <Http>true</Http>
          <Https><xsl:value-of select="$s3-https" /></Https>
          <Hostname>
            <xsl:value-of select="$s3-host" />
          </Hostname>
        </Endpoint>
      </Region>
      <xsl:apply-templates select="@*|node()"/>
    </xsl:copy>
  </xsl:template>

  <xsl:template match="Service[Name='sns' or Name='sqs' or Name='s3']">
    <xsl:copy>
      <xsl:apply-templates select="@*|node()"/>
      <RegionName>
        <xsl:value-of select="$region" />
      </RegionName>
    </xsl:copy>
  </xsl:template>
</xsl:stylesheet>
