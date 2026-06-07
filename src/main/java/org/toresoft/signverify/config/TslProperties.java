package org.toresoft.signverify.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.tsl")
public class TslProperties {

  private List<Source> sources = List.of();
  private Refresh refresh = new Refresh();

  public List<Source> getSources() { return sources; }
  public void setSources(List<Source> s) { this.sources = s; }
  public Refresh getRefresh() { return refresh; }
  public void setRefresh(Refresh r) { this.refresh = r; }

  public static class Source {
    private String id;
    private String type;        // LOTL | TL
    private String url;
    private boolean pivotSupport;
    private String ojKeystorePath;
    private String ojKeystorePasswordEnv;
    private String ojUrl;

    public String getId() { return id; }
    public void setId(String v) { this.id = v; }
    public String getType() { return type; }
    public void setType(String v) { this.type = v; }
    public String getUrl() { return url; }
    public void setUrl(String v) { this.url = v; }
    public boolean isPivotSupport() { return pivotSupport; }
    public void setPivotSupport(boolean v) { this.pivotSupport = v; }
    public String getOjKeystorePath() { return ojKeystorePath; }
    public void setOjKeystorePath(String v) { this.ojKeystorePath = v; }
    public String getOjKeystorePasswordEnv() { return ojKeystorePasswordEnv; }
    public void setOjKeystorePasswordEnv(String v) { this.ojKeystorePasswordEnv = v; }
    public String getOjUrl() { return ojUrl; }
    public void setOjUrl(String v) { this.ojUrl = v; }
  }

  public static class Refresh {
    private String cron = "0 0 2 * * *";
    private String timezone = "Europe/Rome";
    private String startupMode = "BACKGROUND";  // BACKGROUND | BLOCKING | SKIP

    public String getCron() { return cron; }
    public void setCron(String v) { this.cron = v; }
    public String getTimezone() { return timezone; }
    public void setTimezone(String v) { this.timezone = v; }
    public String getStartupMode() { return startupMode; }
    public void setStartupMode(String v) { this.startupMode = v; }
  }
}
