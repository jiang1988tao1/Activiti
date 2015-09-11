/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.activiti.engine.impl.bpmn.parser.handler;

import java.util.List;
import java.util.Map;

import org.activiti.bpmn.model.BaseElement;
import org.activiti.bpmn.model.EventListener;
import org.activiti.bpmn.model.ImplementationType;
import org.activiti.bpmn.model.Process;
import org.activiti.engine.delegate.event.ActivitiEventType;
import org.activiti.engine.impl.bpmn.data.IOSpecification;
import org.activiti.engine.impl.bpmn.parser.BpmnParse;
import org.activiti.engine.impl.context.Context;
import org.activiti.engine.impl.el.ExpressionManager;
import org.activiti.engine.impl.persistence.entity.ProcessDefinitionEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Joram Barrez
 */
public class ProcessParseHandler extends AbstractBpmnParseHandler<Process> {

  private static final Logger LOGGER = LoggerFactory.getLogger(ProcessParseHandler.class);

  public static final String PROPERTYNAME_DOCUMENTATION = "documentation";

  public Class<? extends BaseElement> getHandledType() {
    return Process.class;
  }

  protected void executeParse(BpmnParse bpmnParse, Process process) {
    if (process.isExecutable() == false) {
      LOGGER.info("Ignoring non-executable process with id='" + process.getId() + "'. Set the attribute isExecutable=\"true\" to deploy this process.");
    } else {
      bpmnParse.getProcessDefinitions().add(transformProcess(bpmnParse, process));
    }
  }

  protected ProcessDefinitionEntity transformProcess(BpmnParse bpmnParse, Process process) {
    ProcessDefinitionEntity currentProcessDefinition = Context.getCommandContext().getProcessDefinitionEntityManager().create();
    bpmnParse.setCurrentProcessDefinition(currentProcessDefinition);

    /*
     * Mapping object model - bpmn xml: processDefinition.id -> generated by activiti engine processDefinition.key -> bpmn id (required) processDefinition.name -> bpmn name (optional)
     */
    currentProcessDefinition.setKey(process.getId());
    currentProcessDefinition.setName(process.getName());
    currentProcessDefinition.setCategory(bpmnParse.getBpmnModel().getTargetNamespace());
    currentProcessDefinition.setDescription(process.getDocumentation());
    currentProcessDefinition.setDeploymentId(bpmnParse.getDeployment().getId());
    
    if (bpmnParse.getDeployment().getEngineVersion() != null) {
      currentProcessDefinition.setEngineVersion(bpmnParse.getDeployment().getEngineVersion());
    }
    
    createEventListeners(bpmnParse, process.getEventListeners(), currentProcessDefinition);

    ExpressionManager expressionManager = bpmnParse.getExpressionManager();

    for (String candidateUser : process.getCandidateStarterUsers()) {
      currentProcessDefinition.addCandidateStarterUserIdExpression(expressionManager.createExpression(candidateUser));
    }

    for (String candidateGroup : process.getCandidateStarterGroups()) {
      currentProcessDefinition.addCandidateStarterGroupIdExpression(expressionManager.createExpression(candidateGroup));
    }

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("Parsing process {}", currentProcessDefinition.getKey());
    }
    
    bpmnParse.processFlowElements(process.getFlowElements());
    processArtifacts(bpmnParse, process.getArtifacts());

    // parse out any data objects from the template in order to set up the
    // necessary process variables
    Map<String, Object> variables = processDataObjects(bpmnParse, process.getDataObjects());
    if (null != currentProcessDefinition.getVariables()) {
      currentProcessDefinition.getVariables().putAll(variables);
    } else {
      currentProcessDefinition.setVariables(variables);
    }
    
    if (process.getIoSpecification() != null) {
      IOSpecification ioSpecification = createIOSpecification(bpmnParse, process.getIoSpecification());
      currentProcessDefinition.setIoSpecification(ioSpecification);
    }

    return currentProcessDefinition;
  }

  protected void createEventListeners(BpmnParse bpmnParse, List<EventListener> eventListeners, ProcessDefinitionEntity currentProcessDefinition) {

    if (eventListeners != null && !eventListeners.isEmpty()) {
      for (EventListener eventListener : eventListeners) {
        // Extract specific event-types (if any)
        ActivitiEventType[] types = ActivitiEventType.getTypesFromString(eventListener.getEvents());

        if (ImplementationType.IMPLEMENTATION_TYPE_CLASS.equals(eventListener.getImplementationType())) {
          currentProcessDefinition.getEventSupport().addEventListener(bpmnParse.getListenerFactory().createClassDelegateEventListener(eventListener), types);
        
        } else if (ImplementationType.IMPLEMENTATION_TYPE_DELEGATEEXPRESSION.equals(eventListener.getImplementationType())) {
          currentProcessDefinition.getEventSupport().addEventListener(bpmnParse.getListenerFactory().createDelegateExpressionEventListener(eventListener), types);
        
        } else if (ImplementationType.IMPLEMENTATION_TYPE_THROW_SIGNAL_EVENT.equals(eventListener.getImplementationType())
            || ImplementationType.IMPLEMENTATION_TYPE_THROW_GLOBAL_SIGNAL_EVENT.equals(eventListener.getImplementationType())
            || ImplementationType.IMPLEMENTATION_TYPE_THROW_MESSAGE_EVENT.equals(eventListener.getImplementationType())
            || ImplementationType.IMPLEMENTATION_TYPE_THROW_ERROR_EVENT.equals(eventListener.getImplementationType())) {
        
          currentProcessDefinition.getEventSupport().addEventListener(bpmnParse.getListenerFactory().createEventThrowingEventListener(eventListener), types);
        
        } else {
          LOGGER.warn("Unsupported implementation type for EventListener: " + eventListener.getImplementationType() + " for element " + bpmnParse.getCurrentFlowElement().getId());
        }
      }
    }

  }

}
