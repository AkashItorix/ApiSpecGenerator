package com.itorix.apiwiz;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itorix.apiwiz.model.ApiEndpoint;
import io.swagger.util.Json;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.servers.Server;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.json.JSONObject;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import org.reflections.util.ConfigurationBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;

public class ApiScanner {

  public static void main(String[] args) throws MalformedURLException, JsonProcessingException {

      System.out.println("ARGUMENTS    : " + new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(args));
//    System.out.println("Scanning for API endpoints...");

    String projectBasedir = args[0];
    String fileLocation = args[1];
    File projectRoot = new File(projectBasedir);

    List<URL> urls = new ArrayList<>();

    findTargetClasses(projectRoot, urls);
    URLClassLoader classLoader = new URLClassLoader(urls.toArray(new URL[0]),
      Thread.currentThread().getContextClassLoader());
    Thread.currentThread().setContextClassLoader(classLoader);

    Set<Class<?>> controllers = new HashSet<>();
    Reflections reflections = new Reflections(new ConfigurationBuilder()
      .addUrls(urls)
      .addClassLoaders(classLoader)
      .setScanners(Scanners.TypesAnnotated));
    controllers.addAll(reflections.getTypesAnnotatedWith(RestController.class));
    controllers.addAll(reflections.getTypesAnnotatedWith(Controller.class));

    List<ApiEndpoint> apiEndpoints = new ArrayList<>();

    OpenAPI openAPI = new OpenAPI();
    if(openAPI.getInfo() == null){
        openAPI.setInfo(new Info());
    }
    if(openAPI.getPaths() == null ){
        openAPI.setPaths(new Paths());
    }
    openAPI.getInfo().setTitle("");
    openAPI.getInfo().setVersion("1.0.0");
    openAPI.getInfo().setDescription("");
    Server server = new Server();
    server.setUrl("http://localhost:8000");
    if(openAPI.getServers() == null){
        openAPI.setServers(new ArrayList<>());
    }
    openAPI.getServers().add(server);
    for (Class<?> controller : controllers) {
      try {
        boolean isInterface = controller.isInterface();
        List<Method> accessibleMethods = getSafeMethods(controller);
        for (Method method : accessibleMethods) {
          try {
            String url = getMappingUrl(controller, method);
//              System.out.println("Controller URL  :  "  + url);
            if (url != null) {
              String methodType = getMethod(method);
              if (!checkApiExists(isInterface, apiEndpoints, url, methodType, method.getName())) {
                  PathItem pathItem = new PathItem();
                  if(openAPI.getPaths().get(url) != null){
                      pathItem = openAPI.getPaths().get(url);
                  }
                Operation operation = new Operation();
                if(operation.getParameters() == null){
                    operation.setParameters(new ArrayList<>());
                }
                operation.setOperationId(method.getName());
                boolean bodyExists = checkBodyExists(method.getParameters());
                List<Parameter> bodyParams = new ArrayList<>();
                for (Parameter parameter : method.getParameters()) {
                  try {
                    String paramType = getParameterType(parameter,bodyExists);
                    if (paramType.equals("Body")) {
                      try {
                        operation.setRequestBody(getRequestBody(parameter.getParameterizedType()));
                      } catch (Exception e) {
//                        System.out.println("Warning: Could not process request body: " + e.getMessage());
                      }
                    }else if(!bodyExists && (paramType.equals("ModelAttribute") || paramType.equals("RequestPart"))){
                      bodyParams.add(parameter);
                    }else if (!paramType.isEmpty()) {
                      String paramName = getParamName(parameter);
                        io.swagger.v3.oas.models.parameters.Parameter parameter1 = new io.swagger.v3.oas.models.parameters.Parameter();
                        parameter1.setName(paramName);
                        parameter1.setIn(paramType.toLowerCase());
                        Schema<?> schema = new Schema<>();
                        String parameterType = parameter.getType().getSimpleName().toLowerCase();
                        if(parameterType.equals("int")){
                            schema.setType("integer");
                        } else if (parameterType.equals("long")) {
                            schema.setType("number");
                            schema.setFormat("long");
                        } else if (parameterType.equals("byte")) {
                            schema.setType("string");
                            schema.setFormat("byte");
                        }else if (parameterType.equals("httpheaders") || parameterType.equals("path")) {
                            schema.setType("object");
                        }else if (parameterType.equals("hashmap")) {
                            schema.setType("object");
                        }
                        else if (parameterType.equals("multipartfile") || parameterType.equals("file")) {
                            schema.setType("string");
                            schema.setFormat("binary");
                        }
                        else if(parameterType.equalsIgnoreCase("void")){
                            schema = new Schema<>();
                        } else if (parameterType.equalsIgnoreCase("map")) {
                            schema.setType("object");
                        }else{
                            schema.setType(parameterType);
                        }
                        parameter1.setSchema(schema);
                        operation.getParameters().add(parameter1);
                    }
                  } catch (NoClassDefFoundError | Exception e) {
//                    System.out.println("Warning: Could not process parameter: " + e.getMessage());
                  }
                }
                if(!bodyParams.isEmpty()) {
                    operation.setRequestBody(getRequestBodyFieldsSchema(bodyParams));
                }
                try {
                    operation.setResponses(extractResponseBodies(method));
                } catch (NoClassDefFoundError | Exception e) {
//                  System.out.println("Warning: Could not extract response body: " + e.getMessage());
                }
                switch (methodType){
                    case "GET":
                        pathItem.setGet(operation);
                        break;
                    case "POST":
                        pathItem.setPost(operation);
                        break;
                    case "PUT":
                        pathItem.setPut(operation);
                        break;
                    case "DELETE":
                        pathItem.setDelete(operation);
                        break;
                    case "PATCH":
                        pathItem.setPatch(operation);
                        break;
                }
                openAPI.getPaths().put(url,pathItem);
              }
            }
          } catch (NoClassDefFoundError | Exception e) {
//            System.out.println("Warning: Skipping method " + method.getName() +
//              " in " + controller.getName() + ": " + e.getMessage());
          }
        }
      } catch (Exception ex) {
//        System.out.println("Warning: Could not process controller " +
//          controller.getName() + ": " + ex.getMessage());
        ex.printStackTrace();
      }
    }

      String openAPIFile = fileLocation + UUID.randomUUID().toString().replaceAll("[^A-Za-z]", "").substring(0, 5) + "-openapi.json";
    if(openAPI.getPaths() != null && !openAPI.getPaths().isEmpty()) {
        String pretty = Json.pretty(openAPI);
        pretty = pretty.replaceAll("\"exampleSetFlag\"\\s*:\\s*true\\s*,?", "");
        pretty = pretty.replaceAll("\"exampleSetFlag\"\\s*:\\s*false\\s*,?", "");
        pretty = pretty.replaceAll("\"valueSetFlag\"\\s*:\\s*true\\s*,?", "");
        pretty = pretty.replaceAll("\"valueSetFlag\"\\s*:\\s*false\\s*,?", "");
        pretty = pretty.replaceAll("\"style\"\\s*:\\s*\"SIMPLE\"", "\"style\": \"simple\"");
        pretty = pretty.replaceAll("\"style\"\\s*:\\s*\"FORM\"", "\"style\": \"form\"");
        pretty = pretty.replaceAll("\"type\"\\s*:\\s*\"OAUTH2\"", "\"type\": \"oauth2\"");
        pretty = pretty.replaceAll("\"type\"\\s*:\\s*\"HTTP\"", "\"type\": \"http\"");
        pretty = pretty.replaceAll("\"type\"\\s*:\\s*\"OPENIDCONNECT\"", "\"type\": \"openIdConnect\"");
        pretty = pretty.replaceAll("\"type\"\\s*:\\s*\"APIKEY\"", "\"type\": \"apiKey\"");
        pretty = pretty.replaceAll(",\\s*}", " }");
//        System.out.println(pretty);
        try (FileWriter writer = new FileWriter(openAPIFile)) {
            writer.write(pretty);
        } catch (IOException e) {
        }
    }
  }

    private static io.swagger.v3.oas.models.parameters.RequestBody getRequestBody(Type type) {
        io.swagger.v3.oas.models.parameters.RequestBody requestBody = new io.swagger.v3.oas.models.parameters.RequestBody();
        io.swagger.v3.oas.models.media.Content content = new io.swagger.v3.oas.models.media.Content();
        MediaType mediaType = new MediaType();
        content.put("application/json",mediaType);
        Schema<?> jsonObject = new Schema();
        try {
            Class<?> bodyClass = extractClassFromType(type, jsonObject);
            if (bodyClass != null) {
                processFieldsSchema(bodyClass, jsonObject, new HashSet<>());
                mediaType.setSchema(jsonObject);
            }

        } catch (NoClassDefFoundError | Exception ex) {
//            System.out.println("Warning: Could not process body fields for " + type.getTypeName() + ": " + ex.getMessage());
        }
        requestBody.setContent(content);
        return requestBody;
    }


    private static io.swagger.v3.oas.models.parameters.RequestBody getRequestBodyFieldsSchema(List<Parameter> bodyParams) {
        io.swagger.v3.oas.models.parameters.RequestBody requestBody = new io.swagger.v3.oas.models.parameters.RequestBody();
        Schema<?> jsonObject = new Schema<>();
        jsonObject.setType( "object");
        Content content = new Content();
        MediaType mediaType = new MediaType();
        content.put("application/json",mediaType);
        requestBody.setContent(content);

        Map<String, Schema> properties = new HashMap<>();

        try {
            for (Parameter param : bodyParams) {
                String paramName = getParamName(param);
//                System.out.println("RequestPart param name : " + paramName);
                if (param.isAnnotationPresent(ModelAttribute.class)) {
                    properties.put(paramName,getBodyFieldsSchema(param.getParameterizedType()));
                } else if (param.isAnnotationPresent(RequestPart.class)) {
                    properties.put(paramName,getBodyFieldsSchema(param.getParameterizedType()));
                }

            }
            jsonObject.setProperties(properties);
            mediaType.setSchema(jsonObject);

            return requestBody;
        } catch (Exception ex) {
//            System.out.println("Warning: Could not process body fields: " + ex.getMessage());
        }
        return requestBody;
    }

  private static boolean checkBodyExists(Parameter[] parameters) {
    for (Parameter parameter : parameters) {
      if (parameter.isAnnotationPresent(RequestBody.class)) {
        return true;
      }
    }
    return false;
  }

  private static String getParamName(Parameter parameter) {
    String paramName = parameter.getName();
    RequestHeader headerAnnotation = parameter.getAnnotation(RequestHeader.class);
    if (headerAnnotation != null && !headerAnnotation.value().isEmpty()) {
      paramName = headerAnnotation.value();
    }
    RequestParam paramAnnotation = parameter.getAnnotation(RequestParam.class);
    if (paramAnnotation != null && !paramAnnotation.value().isEmpty()) {
      paramName = paramAnnotation.value();
    }
    PathVariable pathParam = parameter.getAnnotation(PathVariable.class);
    if(pathParam != null && !pathParam.value().isEmpty()){
      paramName = pathParam.value();
    }
    ModelAttribute modelAttribute = parameter.getAnnotation(ModelAttribute.class);
    if(modelAttribute != null && !modelAttribute.value().isEmpty()){
      paramName = modelAttribute.value();
    }
    RequestPart requestPart = parameter.getAnnotation(RequestPart.class);
    if(requestPart != null && !requestPart.value().isEmpty()){
      paramName = requestPart.value();
    }
    return paramName;
  }

  private static List<Method> getSafeMethods(Class<?> controller) {
    List<Method> accessibleMethods = new ArrayList<>();
    try {
      Method[] declaredMethods = controller.getDeclaredMethods();
      for (Method method : declaredMethods) {
        if (java.lang.reflect.Modifier.isPublic(method.getModifiers()) &&
          (method.isAnnotationPresent(RequestMapping.class) ||
            method.isAnnotationPresent(GetMapping.class) ||
            method.isAnnotationPresent(PostMapping.class) ||
            method.isAnnotationPresent(PutMapping.class) ||
            method.isAnnotationPresent(DeleteMapping.class) ||
            method.isAnnotationPresent(PatchMapping.class))) {
          try {
            method.getParameterTypes();
            method.getReturnType();
            accessibleMethods.add(method);
          } catch (NoClassDefFoundError | TypeNotPresentException e) {
//            System.out.println("Skipping method " + method.getName() +
//              " due to unavailable dependency: " + e.getMessage());
          }
        }
      }
    } catch (NoClassDefFoundError e) {
//      System.out.println("Could not access methods due to missing dependency: " + e.getMessage());
    }
    return accessibleMethods;
  }

  private static boolean checkApiExists(boolean isInterface, List<ApiEndpoint> apiEndpoints,
    String url, String methodType, String name) {
    if(url.endsWith("/")){
      url = url.substring(0, url.length()-1);
    }
    if(isInterface){
      String finalUrl = url;
      apiEndpoints.removeIf(apiEndpoint ->
        Objects.equals(apiEndpoint.getMethod(), methodType) &&
          Objects.equals(apiEndpoint.getMethodName(), name) &&
          (Objects.equals(apiEndpoint.getUrl(), finalUrl) ||
            (apiEndpoint.getUrl() != null && apiEndpoint.getUrl().length() > 1 &&
              apiEndpoint.getUrl().substring(0, apiEndpoint.getUrl().length() - 1).equals(finalUrl)))
      );
    }else {
      String finalUrl = url;
      Optional<ApiEndpoint> apiEndpointStream = apiEndpoints.stream().filter(apiEndpoint ->
        Objects.equals(apiEndpoint.getMethod(), methodType) &&
          Objects.equals(apiEndpoint.getMethodName(), name) &&
          apiEndpoint.isInterface() &&
          (Objects.equals(apiEndpoint.getUrl(), finalUrl) ||
            (apiEndpoint.getUrl() != null && apiEndpoint.getUrl().length() > 1 &&
              apiEndpoint.getUrl().substring(0, apiEndpoint.getUrl().length() - 1)
                .equals(finalUrl)))
      ).findAny();
      return apiEndpointStream.isPresent();
    }
    return false;
  }

  private static String getMethod(Method method) {
    if (method.isAnnotationPresent(GetMapping.class)) {
      return HttpMethod.GET.name();
    } else if (method.isAnnotationPresent(PostMapping.class)) {
      return HttpMethod.POST.name();
    } else if (method.isAnnotationPresent(PutMapping.class)) {
      return HttpMethod.PUT.name();
    } else if (method.isAnnotationPresent(DeleteMapping.class)) {
      return HttpMethod.DELETE.name();
    } else if (method.isAnnotationPresent(PatchMapping.class)) {
      return HttpMethod.PATCH.name();
    } else if (method.isAnnotationPresent(RequestMapping.class)) {
      RequestMapping annotation = method.getAnnotation(RequestMapping.class);
      if (annotation.method() != null && annotation.method().length > 0) {
        return annotation.method()[0].name();
      }
    }
    return "";
  }

  private static String getParameterType(Parameter parameter, boolean bodyExists) {
    for (Annotation annotation : parameter.getAnnotations()) {
      if (annotation instanceof RequestHeader)
        return "Header";
      if (annotation instanceof RequestParam)
        return "Query";
      if (annotation instanceof PathVariable)
        return "Path";
      if (annotation instanceof RequestBody)
        return "Body";
      if (annotation instanceof ModelAttribute)
        return bodyExists ? "Query" : "ModelAttribute";
      if (annotation instanceof RequestPart)
        return bodyExists ? "Query" : "RequestPart";
    }
    return "";
  }


    private static Schema<?> getBodyFieldsSchema(Type type) {
        Schema<?> jsonObject = new Schema<>();
        try {
            Class<?> bodyClass = extractClassFromType(type, jsonObject);
            if (bodyClass != null) {
                processFieldsSchema(bodyClass, jsonObject, new HashSet<>());
            }
            return jsonObject;
        } catch (NoClassDefFoundError | Exception ex) {
//            System.out.println("Warning: Could not process body fields for " +
//                    type.getTypeName() + ": " + ex.getMessage());
        }
        return jsonObject;
    }

    private static Schema<?> getSchemaObject(Type type) {
        Schema<?> schema = new Schema<>();
        try {
            Class<?> bodyClass = extractClassFromType(type, schema);
            if (bodyClass != null) {
                processFieldsSchema(bodyClass, schema, new HashSet<>());
                return schema;
            }

        } catch (NoClassDefFoundError | Exception ex) {
            schema = new ObjectSchema();
        }
        return schema;
    }

  private static Class<?> extractClassFromType(Type type, Schema<?> schema ) {
    if (type instanceof Class<?> ) {
      Class<?> clazz = (Class<?>) type;
      if (clazz.isArray()) {
        Class<?> componentType = clazz.getComponentType();
        Schema<?> items = new Schema<>();
        processFieldsSchema(componentType, items, new HashSet<>());
        schema.setType("array");
        schema.setItems(items);
        return null;
      }
      return clazz;
    } else if (type instanceof ParameterizedType) {
      ParameterizedType parameterizedType = (ParameterizedType) type;
      Type rawType = parameterizedType.getRawType();

      if (rawType == List.class || rawType == Collection.class || rawType == Set.class) {
        Type elementType = parameterizedType.getActualTypeArguments()[0];
        Schema<?> itemsJson = new Schema<>();
        processGenericTypeSchema(elementType, itemsJson, new HashSet<>());
          schema.setType("array");
          schema.setItems(itemsJson);
        return null;
      } else if (rawType == Map.class) {
        Type[] typeArguments = parameterizedType.getActualTypeArguments();
        Type valueType = typeArguments.length > 1 ? typeArguments[1] : null;
        Schema<?> nestedJson = new Schema<>();
        processGenericTypeSchema(valueType, nestedJson, new HashSet<>());
        schema.setType("object");
        schema.setAdditionalProperties(nestedJson);
        return null;
      }
      return (Class<?>) rawType;
    }
    return null;
  }

    private static void processFieldsSchema(Class<?> bodyClass, Schema<?> items, Set<Class<?>> visited) {
        if (bodyClass == null || visited.contains(bodyClass)) {
            return;
        }
        visited.add(bodyClass);
        if (bodyClass.isArray()) {
            Class<?> componentType = bodyClass.getComponentType();
            Schema<?> itemsJson = new Schema<>();
            processFieldsSchema(componentType, itemsJson, visited);
            items.setType("array");
            items.setItems(itemsJson);
            return;
        }
        if(bodyClass.isEnum()){
            items.setType("string");
        }
        else if (bodyClass.isPrimitive() || bodyClass.getName().startsWith("java.") ||
                bodyClass.getName().startsWith("javax.")) {
            String parameterType = bodyClass.getSimpleName().toLowerCase();
            if(parameterType.equals("int")){
                items.setType("integer");
            } else if (parameterType.equals("long")) {
                items.setType("number");
                items.setFormat("long");
            } else if (parameterType.equals("byte")) {
                items.setType("string");
                items.setFormat("byte");
            }else if (parameterType.equals("httpheaders")  || parameterType.equals("path")) {
                items.setType("object");
            }else if (parameterType.equals("hashmap")) {
                items.setType("object");
            }
            else if (parameterType.equals("multipartfile") || parameterType.equals("file")) {
                items.setType("string");
                items.setFormat("binary");
            }else if(parameterType.equalsIgnoreCase("void")){
                items = new Schema<>();
            } else if (parameterType.equalsIgnoreCase("map")) {
                items.setType("object");
            }else{
                items.setType(parameterType);
            }
        } else {
            items.setType("object");
            Map<String, Schema> properties = new HashMap<>();
            items.setProperties(properties);
            try {
                for (Field field : bodyClass.getDeclaredFields()) {
                    field.setAccessible(true);
                    String fieldName = field.getName();
                    Type fieldType = field.getGenericType();
                    Schema<?> fieldSchema = new Schema<>();
                    if (fieldType instanceof ParameterizedType) {
                        processParameterizedTypeSchema((ParameterizedType) fieldType, fieldSchema, fieldName, visited);
                    }
//          }
                    else if (field.getType().isArray()) {
                        Class<?> componentType = field.getType().getComponentType();
                        Schema<?> itemsJson = new Schema<>();
                        processFieldsSchema(componentType, itemsJson, visited);
                        fieldSchema.setType("array");
                        fieldSchema.setItems(itemsJson);
                    } else {
                        processFieldsSchema(field.getType(), fieldSchema, visited);
                    }
                    properties.put(fieldName, fieldSchema);
                }
            } catch (NoClassDefFoundError | Exception ex) {
//                System.out.println("Warning: Error processing fields for " + bodyClass.getName() + ": " + ex.getMessage());
            }
        }
        visited.remove(bodyClass);
    }



    private static void processParameterizedTypeSchema(ParameterizedType type, Schema<?> jsonObject, String fieldName, Set<Class<?>> visited) {
        Type rawType = type.getRawType();
        if (rawType == List.class || rawType == Set.class || rawType == Collection.class) {
            Schema<?> itemsJson = new Schema<>();
            Type elementType = type.getActualTypeArguments()[0];
            processGenericTypeSchema(elementType, itemsJson, visited);
            jsonObject.setName(fieldName);
            jsonObject.setType("array");
            jsonObject.setItems(itemsJson);
        } else if (rawType == Map.class) {
            Schema<?> additionalPropsJson = new Schema<>();
            Type[] typeArguments = type.getActualTypeArguments();
            Type valueType = typeArguments.length > 1 ? typeArguments[1] : null;
            processGenericTypeSchema(valueType, additionalPropsJson, visited);
            jsonObject.setName(fieldName);
            jsonObject.setType("object");
            jsonObject.setAdditionalProperties(additionalPropsJson);
        }
    }


    private static void processGenericTypeSchema(Type type, Schema<?> jsonObject, Set<Class<?>> visited) {
        if (type instanceof Class<?>) {
            processFieldsSchema((Class<?>) type, jsonObject, visited);
        } else if (type instanceof ParameterizedType) {
            ParameterizedType paramType = (ParameterizedType) type;
            Type rawType = paramType.getRawType();

            if (rawType == List.class || rawType == Collection.class || rawType == Set.class) {
                Schema<?> itemsJson = new Schema<>();
                processGenericTypeSchema(paramType.getActualTypeArguments()[0], itemsJson, visited);
                jsonObject.setType("array");
                jsonObject.setItems(itemsJson);
            } else if (rawType == Map.class) {
                Schema<?> additionalPropsJson = new Schema<>();
                Type[] typeArguments = paramType.getActualTypeArguments();
                processGenericTypeSchema(typeArguments[1], additionalPropsJson, visited);
                jsonObject.setType("object");
                jsonObject.setAdditionalProperties(additionalPropsJson);
            } else {
                String parameterType = rawType.getTypeName().toLowerCase();
                if(parameterType.equals("int")){
                    jsonObject.setType("integer");
                } else if (parameterType.equals("long")) {
                    jsonObject.setType("number");
                    jsonObject.setFormat("long");
                } else if (parameterType.equals("byte")) {
                    jsonObject.setType("string");
                    jsonObject.setFormat("byte");
                }
                else if (parameterType.equals("httpheaders") || parameterType.equals("path")) {
                    jsonObject.setType("object");
                }
                else if (parameterType.equals("multipartfile") || parameterType.equals("file")) {
                    jsonObject.setType("string");
                    jsonObject.setFormat("binary");
                }
                else if (parameterType.equals("hashmap")) {
                    jsonObject.setType("object");
                }else if(parameterType.equalsIgnoreCase("void")){
                } else if (parameterType.equalsIgnoreCase("map")) {
                    jsonObject.setType("object");
                } else{
                    jsonObject.setType(parameterType);
                }
            }
        } else {
            jsonObject.setType("unknown");
        }
    }


  private static String getMappingUrl(Class<?> controller, Method method) {
    String baseUrl = "";
    if (controller.isAnnotationPresent(RequestMapping.class)) {
      RequestMapping annotation = controller.getAnnotation(RequestMapping.class);
      baseUrl = annotation.value().length > 0 ? annotation.value()[0] : "";
    }

    if (method.isAnnotationPresent(GetMapping.class)) {
      GetMapping annotation = method.getAnnotation(GetMapping.class);
      return baseUrl + (annotation.value().length > 0 ? annotation.value()[0] : "");
    } else if (method.isAnnotationPresent(PostMapping.class)) {
      PostMapping annotation = method.getAnnotation(PostMapping.class);
      return baseUrl + (annotation.value().length > 0 ? annotation.value()[0] : "");
    } else if (method.isAnnotationPresent(PutMapping.class)) {
      PutMapping annotation = method.getAnnotation(PutMapping.class);
      return baseUrl + (annotation.value().length > 0 ? annotation.value()[0] : "");
    } else if (method.isAnnotationPresent(DeleteMapping.class)) {
      DeleteMapping annotation = method.getAnnotation(DeleteMapping.class);
      return baseUrl + (annotation.value().length > 0 ? annotation.value()[0] : "");
    } else if (method.isAnnotationPresent(PatchMapping.class)) {
      PatchMapping annotation = method.getAnnotation(PatchMapping.class);
      return baseUrl + (annotation.value().length > 0 ? annotation.value()[0] : "");
    } else if (method.isAnnotationPresent(RequestMapping.class)) {
      RequestMapping annotation = method.getAnnotation(RequestMapping.class);
      return baseUrl + (annotation.value().length > 0 ? annotation.value()[0] : "");
    }
    return null;
  }


    private static ApiResponses extractResponseBodies(Method method) throws JsonProcessingException {
        ApiResponses responses = new ApiResponses();
        ApiResponse response = new ApiResponse();
        response.setDescription("");
        Content content = new Content();
        MediaType mediaType = new MediaType();
        Schema<?> schema = new Schema<>();
        content.put("application/json",mediaType);
        response.setContent(content);
        responses.put("200",response);
        Class<?> returnType = method.getReturnType();
        if (!returnType.equals(void.class) && !returnType.equals(Void.class)) {
            if (returnType.getName().contains("ResponseEntity")) {
                Type genericReturnType = method.getGenericReturnType();
                if (genericReturnType instanceof ParameterizedType) {
                    ParameterizedType paramType = (ParameterizedType) genericReturnType;
                    Type[] typeArgs = paramType.getActualTypeArguments();
                    if (typeArgs.length > 0) {
                        Type actualType = typeArgs[0];
                        String typeName = actualType.getTypeName();
                        if (typeName.equals("?")) {
                            schema = new ObjectSchema();
                            mediaType.setSchema(schema);
                        } else if (typeName.equalsIgnoreCase("void")) {
                            schema = new Schema<>();
                            mediaType.setSchema(schema);
                        } else {
                            schema = getSchemaObject(actualType);
                            mediaType.setSchema(schema);
                        }
                    }
                }
            } else {
                schema =  getBodyFieldsSchema(returnType);
                mediaType.setSchema(schema);
            }
        }
        return responses;
    }
  private static void findTargetClasses(File dir, List<URL> urls) throws MalformedURLException {
    if (!dir.exists()) return;

    File targetClasses = new File(dir, "target/classes");
    if (targetClasses.exists()) {
      urls.add(targetClasses.toURI().toURL());
//      System.out.println("Added classes directory: " + targetClasses.getAbsolutePath());
    }

    File[] subDirs = dir.listFiles(File::isDirectory);
    if (subDirs != null) {
      for (File subDir : subDirs) {
        if (subDir.getName().startsWith(".") ||
          subDir.getName().equals("target") ||
          subDir.getName().equals("node_modules")) {
          continue;
        }
        findTargetClasses(subDir, urls);
      }
    }
  }
}