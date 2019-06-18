/*
 * Copyright 2019-present Open Networking Foundation
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
package org.foo.app;
//Imports for CLI app
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.onosproject.cli.AbstractShellCommand;

//Imports for "devices"
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.collect.ImmutableSet;
import org.apache.karaf.shell.api.action.Option;
import org.onosproject.net.AnnotationKeys;
import org.onosproject.net.Device;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.driver.DriverService;
import org.onosproject.utils.Comparators;
import java.util.Collections;
import java.util.List;
import static com.google.common.collect.Lists.newArrayList;

//Imports for "netconf-get"

import static com.google.common.base.Preconditions.checkNotNull;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Completion;
import org.onosproject.cli.net.DeviceIdCompleter;
import org.onosproject.net.DeviceId;
import org.onosproject.netconf.NetconfController;
import org.onosproject.netconf.NetconfDevice;
import org.onosproject.netconf.NetconfException;
import org.onosproject.netconf.NetconfSession;

//Imports for XML Parse

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;
import org.xml.sax.InputSource;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.text.DateFormat;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerConfigurationException;
import java.util.Map;
import java.util.HashMap;

//Imports for timer
import java.util.concurrent.ScheduledExecutorService;
import static java.util.concurrent.TimeUnit.*;
import java.util.concurrent.Executors;

//Imports for File writing
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

//Imports for date
import java.util.Date;
import java.text.SimpleDateFormat;

/**
 * Sample Apache Karaf CLI command
 */
@Service
@Command(scope = "onos", name = "stats",
    description = "Get statistics periodically from NETCONF devices")
public class StatsCommand extends AbstractShellCommand {

    //String to structure content showed by "devices"
    private static final String FMT =
        "id=%s, available=%s, local-status=%s, role=%s, type=%s, mfr=%s, hw=%s, sw=%s, " +
        "serial=%s, chassis=%s, driver=%s%s";
    //timeout used for various purposes in NETCONF sessions
    long timeoutSec = 30;
    //Map saving previous statistics in order to calculate flows
    private static Map < String, Integer > previousStats = new HashMap < String, Integer > ();
    //Nombre de interfaz usado para crear archivos y leerlos
    private static String nameOfInterface;

    //doExecute is called when "stats" is called on ONOS CLI
    @Override
    protected void doExecute() {
        print("Application to recolect statistics from NETCONF devices [VÍCTOR GARCÍA GONZÁLEZ]");
        previousStats.clear();

        //Call the proper method to recolect statistics every other 15 seconds
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
        executor.scheduleAtFixedRate(statsRunnable, 0, 15, TimeUnit.SECONDS);




    }


    /**
     * Returns JSON node representing the specified devices.
     *
     * @param devices collection of devices
     * @return JSON node
     */
    private JsonNode json(Iterable < Device > devices) {
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode result = mapper.createArrayNode();
        for (Device device: devices) {
            result.add(jsonForEntity(device, Device.class));
        }
        return result;
    }

    /**
     * Returns the list of devices sorted using the device ID URIs.
     *
     * @param service device service
     * @return sorted device list
     */
    public static List < Device > getSortedDevices(DeviceService service) {
        List < Device > devices = newArrayList(service.getDevices());
        Collections.sort(devices, Comparators.ELEMENT_COMPARATOR);
        return devices;
    }

    //printXML was used for debug purposes. It's useful to see the navigation through the DOM. Prints the node as a string
    protected void printXML(Node node) {
        StringWriter sw = new StringWriter();
        try {
            Transformer serializer = TransformerFactory.newInstance().newTransformer();

            serializer.transform(new DOMSource(node), new StreamResult(sw));
        } catch (TransformerConfigurationException e) {
            e.printStackTrace();
        } catch (TransformerException e) {
            e.printStackTrace();
        }
        String result = sw.toString();
        print(result);
    }


    Runnable statsRunnable = new Runnable() {
        public void run() {
            DeviceService deviceService = get(DeviceService.class);
            if (outputJson()) {
                print("%s", json(getSortedDevices(deviceService)));
            } else {
                for (Device device: getSortedDevices(deviceService)) {
                    //printDevice(deviceService, device);

                    // Code to call the command "netconf-get" for each device. The reply is saved in the variable res

                    DeviceId deviceId = device.id();
                    NetconfController controller = get(NetconfController.class);
                    checkNotNull(controller, "Netconf controller is null");
                    NetconfDevice device2 = controller.getDevicesMap().get(deviceId);
                    if (device2 == null) {
                        print("Netconf device object not found for %s", deviceId);
                        return;
                    }
                    NetconfSession session = device2.getSession();
                    if (session == null) {
                        print("Netconf session not found for %s", deviceId);
                        return;
                    }
                    try {
                        print("----------------------------------------DEVICE: " + device.id() + "-----------------------------------------------");
                        CharSequence res = session.asyncGet()
                            .get(timeoutSec, TimeUnit.SECONDS);

                        //Uncomment next line to print the reply to netconf-get (<data>)
                        //  print("%s", res);

                        //Code to XML Parse from res

                        try {
                            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                            DocumentBuilder builder = factory.newDocumentBuilder();
                            Document doc = builder.parse(new InputSource(new StringReader(res.toString())));
                            NodeList interfacesList = doc.getElementsByTagName("interface");

                            //Code to travel around the DOM tree printing the wanted information

                            for (int i = 0; i < interfacesList.getLength(); i++) {
                                Node interf = interfacesList.item(i);
                                if (interf.getNodeType() == Node.ELEMENT_NODE) {
                                    Element interfE = (Element) interf;
                                    print("--------------INTERFACE--------------");
                                    NodeList interfData = interfE.getChildNodes();
                                    for (int j = 0; j < interfData.getLength(); j++) {
                                        Node d = interfData.item(j);
                                        if (d.getNodeType() == Node.ELEMENT_NODE) {
                                            Element value = (Element) d;
                                            if ((value.getTagName() == "name")) { // || (value.getTagName() == "oper-status")) {
                                                print(value.getTagName() + " = " + value.getTextContent());
                                                // CREAMOS ARCHIVO CON ID DEL DEVICE Y NOMBRE DE LA INTERFAZ
                                                nameOfInterface = value.getTextContent();


                                            }
                                            if (value.getTagName() == "statistics") {
                                                NodeList stats = value.getChildNodes();
                                                for (int k = 0; k < stats.getLength(); k++) {
                                                    Node s = stats.item(k);
                                                    if (s.getNodeType() == Node.ELEMENT_NODE) {
                                                        Element statsValue = (Element) s;
                                                        if (statsValue.getTagName() == "in-octets") {
                                                            if (previousStats.get("Dev"+ deviceId + i + "Int" + k) != null) {
                                                                //Calculation of flow and update of the map
                                                                Integer caudal = (Integer.parseInt(statsValue.getTextContent()) - previousStats.get("Dev" + deviceId + i + "Int" + k)) * 8 / 15 / 1000;
                                                                print("Average input rate [kbps] = " + caudal.toString());
                                                                previousStats.remove("Dev" + deviceId + i + "Int" + k);
                                                                previousStats.put("Dev" + deviceId + i + "Int" + k, Integer.parseInt(statsValue.getTextContent()));
                                                                // CÓDIGO PARA GUARDAR LOS CAUDALES EN UN FICHERO QUE LUEGO SE LEERÁ PARA CREAR LA GRÁFICA
                                                                try{
                                                                File file = new File("/home/victor/Descargas/onos/archivosNETCONF/"+device.id()+nameOfInterface+".txt");
                                                                //file.createNewFile();
                                                                FileWriter fw = new FileWriter(file, true);
                                                                BufferedWriter bw = new BufferedWriter(fw);
                                                                bw.newLine();
                                                                bw.write(caudal.toString());
                                                                //Código para añadir la hora de la muestra al fichero
                                                                DateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");
                                                                Date date = new Date();
                                                                bw.newLine();
                                                                bw.write(dateFormat.format(date));
                                                                bw.flush();
                                                                bw.close(); }
                                                                catch(IOException e){e.printStackTrace();}



                                                            } else {
                                                                //The first time the method is called the map is empty
                                                                print("Average input rate will not be available for another 15 seconds");
                                                                previousStats.put("Dev" + deviceId + i + "Int" + k, Integer.parseInt(statsValue.getTextContent()));
                                                            }
                                                        }

                                                        /*****************************************************************
                                                        CÓDIGO PARA IMPRIMIR TAMBIÉN EL VALOR DE OUT-OCTETS
                                                        if (statsValue.getTagName() == "out-octets") {
                                                            if (previousStats.get("Dev" + i + "Int" + k) != null) {
                                                                //Calculation of flow and update of the map
                                                                Integer caudal = (Integer.parseInt(statsValue.getTextContent()) - previousStats.get("Dev" + i + "Int" + k)) * 8 / 15 / 1000;
                                                                print("Average output rate [kbps] = " + caudal.toString());
                                                                previousStats.remove("Dev" + i + "Int" + k);
                                                                previousStats.put("Dev" + i + "Int" + k, Integer.parseInt(statsValue.getTextContent()));
                                                            } else {
                                                                //The first time the method is called the map is empty
                                                                print("Average output rate will not be available for another 15 seconds");
                                                                previousStats.put("Dev" + i + "Int" + k, Integer.parseInt(statsValue.getTextContent()));
                                                            }
                                                        } *****************************************************************/

                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }


                        } catch (ParserConfigurationException e) {
                            e.printStackTrace();
                        } catch (SAXException e) {
                            e.printStackTrace();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    } catch (NetconfException | InterruptedException | ExecutionException | TimeoutException e) {
                        log.error("Configuration could not be retrieved", e);
                        print("Error occurred retrieving configuration");
                    }
                }
            }
        }
    };

}
