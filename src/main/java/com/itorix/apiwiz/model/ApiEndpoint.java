package com.itorix.apiwiz.model;

import java.util.List;
import java.util.Map;

public class ApiEndpoint {
  private String className;
  private String methodName;
  private String method;
  private String url;
  private List<Parameters> parameters;
  private Object requestBody;
  private Object responseBody;
  private boolean isInterface;

  public String getClassName() {
    return className;
  }

  public void setClassName(String className) {
    this.className = className;
  }

  public String getMethodName() {
    return methodName;
  }

  public void setMethodName(String methodName) {
    this.methodName = methodName;
  }

  public String getMethod() {
    return method;
  }

  public void setMethod(String method) {
    this.method = method;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public List<Parameters> getParameters() {
    return parameters;
  }

  public void setParameters(List<Parameters> parameters) {
    this.parameters = parameters;
  }

  public Object getRequestBody() {
    return requestBody;
  }

  public void setRequestBody(Object requestBody) {
    this.requestBody = requestBody;
  }

  public Object getResponseBody() {
    return responseBody;
  }

  public void setResponseBody(Object responseBody) {
    this.responseBody = responseBody;
  }

  public boolean isInterface() {
    return isInterface;
  }

  public void setInterface(boolean anInterface) {
    isInterface = anInterface;
  }
}
