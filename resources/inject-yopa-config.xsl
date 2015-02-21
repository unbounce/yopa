<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:param name="region" />
  <xsl:param name="host" />
  <xsl:param name="sqs-port" />
  <xsl:param name="sns-port" />
  <xsl:param name="s3-port" />
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
          <Https>false</Https>
          <Hostname>
            <xsl:value-of select="$host" />:<xsl:value-of select="$sqs-port" />
          </Hostname>
        </Endpoint>
        <Endpoint>
          <ServiceName>sns</ServiceName>
          <Http>true</Http>
          <Https>false</Https>
          <Hostname>
            <xsl:value-of select="$host" />:<xsl:value-of select="$sns-port" />
          </Hostname>
        </Endpoint>
        <Endpoint>
          <ServiceName>s3</ServiceName>
          <Http>true</Http>
          <Https>false</Https>
          <Hostname>
            <xsl:value-of select="$host" />:<xsl:value-of select="$s3-port" />
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
