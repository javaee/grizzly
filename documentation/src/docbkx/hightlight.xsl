<?xml version='1.0'?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:fo="http://www.w3.org/1999/XSL/Format"
                xmlns:xslthl="http://xslthl.sf.net"
                exclude-result-prefixes="xslthl"
                version='1.0'>

<xsl:import href="urn:docbkx:stylesheet"/>

<xsl:template match='xslthl:keyword'>
  <i class="hl-keyword"><xsl:apply-templates/></i>
</xsl:template>

<xsl:template match='xslthl:comment'>
  <i class="hl-comment"><xsl:apply-templates/></i>
</xsl:template>

<xsl:template match='xslthl:string'>
  <i class="hl-string"><xsl:apply-templates/></i>
</xsl:template>

</xsl:stylesheet>