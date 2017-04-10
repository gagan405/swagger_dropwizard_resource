import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.google.common.collect.Lists;
import io.swagger.converter.ModelConverters;
import io.swagger.jackson.ModelResolver;
import io.swagger.jaxrs.listing.BaseApiListingResource;
import io.swagger.models.Model;
import io.swagger.models.SecurityRequirement;
import io.swagger.models.Swagger;
import io.swagger.models.auth.ApiKeyAuthDefinition;
import io.swagger.models.auth.In;
import io.swagger.models.auth.SecuritySchemeDefinition;
import io.swagger.models.properties.Property;
import io.swagger.models.properties.RefProperty;
import io.swagger.models.properties.StringProperty;
import org.apache.commons.collections.MapUtils;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@Path("swagger")
public class SwaggerHelperResource extends BaseApiListingResource {

  @Context
  ServletContext context;

  @Path("json")
  @GET
  @Produces({"application/json"})
  public Response getSwaggerSpec(@Context Application app,
                                 @Context ServletConfig sc,
                                 @Context HttpHeaders headers,
                                 @Context UriInfo uriInfo,
                                 @QueryParam("type") String type
  ) throws IOException {

    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.setPropertyNamingStrategy(PropertyNamingStrategy.CAMEL_CASE_TO_LOWER_CASE_WITH_UNDERSCORES);
    ModelConverters.getInstance().addConverter(new ModelResolver(objectMapper));

    Response response = type.equals("json") ? getListingJsonResponse(app, context, sc, headers, uriInfo): getListingYamlResponse(app, context, sc, headers, uriInfo);
    if(response.getStatus() == 200){

      Swagger swagger = (Swagger) response.getEntity();
      overrideDateTimeDefinitions(swagger);
      ApiKeyAuthDefinition apiKeyAuthDefinition = new ApiKeyAuthDefinition("authorization", In.HEADER);
      Map<String, SecuritySchemeDefinition> map = new HashMap<>();
      map.put("api_key", apiKeyAuthDefinition);
      swagger.setSecurityDefinitions(map);
      SecurityRequirement requirement = new SecurityRequirement();
      requirement.requirement("api_key", new ArrayList<>());
      swagger.setSecurity(Lists.<SecurityRequirement>newArrayList(requirement));

      return writeToJsonFile(swagger);
    }
    return response;
  }

  private void overrideDateTimeDefinitions(Swagger swagger) {
    Map<String, Model> definitions = swagger.getDefinitions();
    for(Map.Entry<String, Model> e : definitions.entrySet()){
      Map<String, Property> propertyMap = e.getValue().getProperties();
      if(MapUtils.isEmpty(propertyMap)) {
        continue;
      }
      for(String key : propertyMap.keySet()){
        Property value = propertyMap.get(key);
        if(value.getType().equals("ref") && ((RefProperty) value).getSimpleRef().equals("LocalDateTime")){
          propertyMap.put(key, new StringProperty("LocalDateTime in ISO format")
                  .example("2016-12-07 11:56:39")
                  .description("ISO format string"));
        }
      }
    }
  }

  private Response writeToJsonFile(Swagger swagger) throws IOException {
    try {

      String filePath = "/usr/share/assets/swagger/dist/swagger.json";
      ObjectMapper mapper = new ObjectMapper();
      mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
      String swaggerJson = mapper.writeValueAsString(swagger);
      try {
        // Convert object to JSON string and save into a file directly
        mapper.writerWithDefaultPrettyPrinter().writeValue(new File(filePath), swagger);
      } catch (JsonGenerationException | JsonMappingException e) {
        e.printStackTrace();
      }
      return Response.ok(swaggerJson).build();
    } catch (JsonProcessingException e) {
      throw new WebApplicationException(e);
    }
  }
}
